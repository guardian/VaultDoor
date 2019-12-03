package controllers

import java.net.URI

import actors.ObjectCache.{Lookup, OCMsg, ObjectFound, ObjectLookupFailed, ObjectNotFound}
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{GraphDSL, Source}
import akka.stream.{Attributes, Materializer, SourceShape}
import auth.Security
import com.om.mxs.client.japi.{MatrixStore, UserInfo, Vault}
import helpers.{BadDataError, OMAccess, OMLocator, RangeHeader, UserInfoCache, ZonedDateTimeEncoder}
import javax.inject.{Inject, Named, Singleton}
import play.api.Configuration
import play.api.cache.SyncCacheApi
import play.api.libs.circe.Circe
import play.api.mvc.{AbstractController, ControllerComponents, ResponseHeader, Result}
import responses.{GenericErrorResponse, KnownVaultResponse}
import io.circe.generic.auto._
import io.circe.syntax._
import models.{AuditEvent, AuditFile, ObjectMatrixEntry}
import play.api.http.HttpEntity
import streamcomponents.{AuditLogFinish, MatrixStoreFileSourceWithRanges, MultipartSource}
import akka.pattern.ask

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class VaultController @Inject() (cc:ControllerComponents,
                                 config:Configuration,
                                 omAccess: OMAccess,
                                 @Named("object-cache") objectCache:ActorRef,
                                 @Named("audit-actor") auditActor:ActorRef,
                                 userInfoCache:UserInfoCache
                                )(implicit mat:Materializer,system:ActorSystem, override implicit val cache:SyncCacheApi)
  extends AbstractController(cc) with Security with ObjectMatrixEntryMixin with Circe with ZonedDateTimeEncoder{

  def knownVaults() = IsAuthenticated { uid=> request=>
    val content = userInfoCache.allKnownVaults()
    val responses = content.map(entry=>
      (KnownVaultResponse.apply _) tupled entry
    )

    Ok(responses.asJson)
  }

  /**
    * execute a block with a vault for the given ID and ensure that the vault connection is disposed of when
    * we finish, regardless of whether an exception occurred
    * @param vaultId the Vault ID (as a string)
    * @param block function to call as a block. Takes the Vault reference as a parameter and should return a Result
    * @return
    */
  def withVaultForId(vaultId:String)(block: Vault=>Future[Result]) = {
    val maybeResult = userInfoCache.infoForVaultId(vaultId) match {
      case Some(userInfo)=>
        val maybeVault = Future { MatrixStore.openVault(userInfo)}
        maybeVault
          .flatMap(v=>block(v).andThen({
            case _=>v.dispose()
          }))
      case None=>Future(NotFound(GenericErrorResponse("not_found","either the vault or file id is not valid").asJson))
    }
    maybeResult.recover({
      case err:Throwable=>
        logger.error(s"Could not perform vault operation: ", err)
        InternalServerError(GenericErrorResponse("server_error", err.getMessage).asJson)
    })
  }

  def withVaultForIdSync(vaultId:String)(block: Vault=>Result) = withVaultForId(vaultId) { v=>Future{block(v)}}

  def getMaybeResponseSize(entry:ObjectMatrixEntry, overriden:Option[Long]):Option[Long] = {
    overriden match {
      case value @Some(_)=>value
      case None=>entry.fileAttribues.map(_.size)
    }
  }

  def getMaybeMimetype(entry:ObjectMatrixEntry):Option[String] = entry.attributes.flatMap(_.stringValues.get("MXFS_MIMETYPE"))

  /**
    * return the metadata for the given oid on the given vault, for a HEAD request
    * @param vaultId vault ID to query
    * @param oid object ID to query
    * @return
    */
  def headTargetContent(vaultId:String, oid:String) = IsAuthenticatedAsync { uid=> request=>
    withVaultForId(vaultId) { implicit vault=>
      val initialEntry = ObjectMatrixEntry(oid)
      initialEntry.getMetadata.map(entry=>
      Result(
        ResponseHeader(200,headersForEntry(entry, Seq(), getMaybeResponseSize(entry, None))),
        HttpEntity.Streamed(Source.empty, getMaybeResponseSize(entry, None), getMaybeMimetype(entry))
      ))
    }
  }

  /**
    * gets a multipart source if needed or just gets a single source if no ranges specified
    * @param ranges a sequence of [[RangeHeader]] objects. If empty a single source for the entire file is returned
    * @param userInfo userInfo object describing the appliance and vault to target
    * @param omEntry [[ObjectMatrixEntry]] instance describing the file to target
    * @return an akka Source that yields ByteString contents of the file
    */
  def getStreamingSource(ranges:Seq[RangeHeader], userInfo:UserInfo, omEntry:ObjectMatrixEntry, auditFile:AuditFile, uid:String) = Try {
    import akka.stream.scaladsl.GraphDSL.Implicits._
    val partialGraph = if(ranges.length>1) {
      val mpSep = MultipartSource.genSeparatorText

      val rangesAndSources = MultipartSource.makeSources(ranges, userInfo, omEntry)

      GraphDSL.create() { implicit builder =>
        val src = builder.add(MultipartSource.getSource(rangesAndSources, omEntry.fileAttribues.get.size, "application/octet-stream", mpSep))
        val audit = builder.add(new AuditLogFinish(auditActor,auditFile,uid, omEntry.fileAttribues.get.size))
        src ~> audit
        SourceShape(audit.out)
      }
    } else {
      GraphDSL.create() { implicit builder=>
        val src = builder.add(new MatrixStoreFileSourceWithRanges(userInfo,omEntry.oid,omEntry.fileAttribues.get.size,ranges))
        val audit = builder.add(new AuditLogFinish(auditActor,auditFile,uid, omEntry.fileAttribues.get.size))
        src ~> audit
        SourceShape(audit.out)
      }
    }
    Source.fromGraph(partialGraph)
  }

  /**
    * third test, use the MatrixStoreFileSourceWithRanges to efficiently stream ranges of content
    * @param targetUriString omms URI of the object that we are trying to get
    * @return
    */
  def streamTargetContent(vaultId:String, oid:String) = IsAuthenticatedAsync { uid=> request=>
    /*
    break down the ranges header into structured data
     */
    val rangesOrFailure = request.headers.get("Range") match {
      case Some(hdr)=>RangeHeader.fromStringHeader(hdr)
      case None=>Success(Seq())
    }

    withVaultForIdSync(vaultId) { implicit vault =>
      val omEntry = ObjectMatrixEntry(oid).getMetadataSync
      /*
    get hold of a streaming source, if possible
     */
      val maybeResult = rangesOrFailure.flatMap(ranges => {
        val userInfo = userInfoCache.infoForVaultId(vaultId)

        val responseSize = if (ranges.nonEmpty) {
          Some(ranges.foldLeft(0L)((acc, range) => acc + (range.end.getOrElse(omEntry.fileAttribues.get.size) - range.start.getOrElse(0L))))
        } else {
          omEntry.fileAttribues.map(_.size)
        }

        logger.debug(s"Ranges is ${ranges}")
        //log that we are starting a streamout
        val auditFile = AuditFile(omEntry.oid, "")
        auditActor ! actors.Audit.LogEvent(AuditEvent.STREAMOUT, uid, Some(auditFile), ranges)

        getStreamingSource(ranges, userInfo.get, omEntry, auditFile, uid) match {
          case Success(partialGraph) =>
            Success((Source.fromGraph(partialGraph), responseSize, headersForEntry(omEntry, ranges, responseSize), getMaybeMimetype(omEntry), ranges.nonEmpty))
          case Failure(err) => //if we did not get a source, log that
            auditActor ! actors.Audit.LogEvent(AuditEvent.OMERROR, uid, Some(auditFile), ranges, notes = Some(err.getMessage))
            logger.error(s"Could not set up streaming source: ", err)
            Failure(new RuntimeException("Could not set up streaming source, see logs for more details"))
        }
      })

      maybeResult match {
        case Success((byteSource, maybeResponseSize, headers, maybeMimetype, isPartialTransfer)) =>
          logger.info(s"maybeResponseSize is $maybeResponseSize")

          Result(
            ResponseHeader(if (isPartialTransfer) 206 else 200, headers),
            HttpEntity.Streamed(byteSource.log("outputstream").addAttributes(
              Attributes.logLevels(
                onElement = Attributes.LogLevels.Info,
                onFailure = Attributes.LogLevels.Error,
                onFinish = Attributes.LogLevels.Info)), None, maybeMimetype)
          ) //we can't give a proper content length, because if we are sending multipart chunks that adds overhead to the request size.
        case Failure(err) => InternalServerError(GenericErrorResponse("error", err.getMessage).asJson)
      }
    }
  }
}
