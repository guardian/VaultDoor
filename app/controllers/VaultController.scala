package controllers

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink, Source}
import akka.stream.{Attributes, ClosedShape, Materializer, SourceShape}
import auth.{BearerTokenAuth, Security}
import com.om.mxs.client.japi.{MatrixStore, SearchTerm, UserInfo, Vault}
import helpers.{RangeHeader, UserInfoCache, ZonedDateTimeEncoder}

import javax.inject.{Inject, Named, Singleton}
import play.api.Configuration
import play.api.cache.SyncCacheApi
import play.api.libs.circe.Circe
import play.api.mvc.{AbstractController, AnyContent, ControllerComponents, Request, ResponseHeader, Result}
import responses.{GenericErrorResponse, KnownVaultResponse, SingleItemDownloadTokenResponse}
import io.circe.generic.auto._
import io.circe.syntax._
import models.{AuditEvent, AuditFile, CachedEntry, ExistingArchiveContentCache, ObjectMatrixEntry, ServerTokenDAO, ServerTokenEntry}
import play.api.http.HttpEntity
import streamcomponents.{AuditLogFinish, MatrixStoreFileSourceWithRanges, MultipartSource, OMFastSearchSource}
import akka.pattern.ask

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class VaultController @Inject() (cc:ControllerComponents,
                                 override implicit val config:Configuration,
                                 override val bearerTokenAuth:BearerTokenAuth,
                                 @Named("audit-actor") auditActor:ActorRef,
                                 userInfoCache:UserInfoCache,
                                 serverTokenDAO: ServerTokenDAO,
                                )(implicit mat:Materializer,system:ActorSystem, override implicit val cache:SyncCacheApi)
  extends AbstractController(cc) with Security with ObjectMatrixEntryMixin with Circe with ZonedDateTimeEncoder{

  def knownVaults() = IsAuthenticated { uid=> request=>
    val content = userInfoCache.byVaultId
    val responses = content.values
      .flatMap(_.headOption)
      .map(KnownVaultResponse.fromBuilder)

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

  def createSingleDownloadToken(vaultId:String, oid:String) = IsAuthenticatedAsync { uid=> request=>
    withVaultForId(vaultId) { _=>
      val expiry = config.getOptional[Int]("serverToken.shortLivedDuration").getOrElse(10)
      val token = ServerTokenEntry.create(Some(s"${vaultId}:${oid}"), forUser=Some(uid))
      serverTokenDAO.put(token, expiry).map({
        case true=>
          Ok(SingleItemDownloadTokenResponse(s"/api/rawdownload/${token.value}").asJson)
        case false=>
          InternalServerError(GenericErrorResponse("error","Could not save server token, see logs for details").asJson)
      })
        .recover({
          case err:Throwable=>
            logger.error(s"Could not save token: ${err.getMessage}", err)
            InternalServerError(GenericErrorResponse("error","Save token operation failed, see server logs").asJson)
        })
    }
  }

  private def splitAssociatedIds(from:Option[String]):Option[(String,String)] = from.flatMap(str=>{
    val parts = str.split(":")
    if(parts.length==2){
      Some((parts.head, parts(1)))
    } else {
      None
    }
  })

  def singleTokenDownload(token:String) = Action.async { request=>
    serverTokenDAO.get(token).flatMap({
      case Some(serverToken)=>
        serverTokenDAO.remove(token)
        splitAssociatedIds(serverToken.associatedId) match {
          case Some((vaultId, oid))=>
            streamTargetContent(vaultId, oid, request, serverToken.createdForUser.getOrElse("anonymous"))
          case None=>
            logger.error(s"Server token $token for ${serverToken.createdForUser} was invalid, did not have vault and object IDs")
            Future(NotFound(GenericErrorResponse("not_found","invalid token").asJson))
        }
      case None=>
        Future(NotFound(GenericErrorResponse("not_found","").asJson))
    })
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
  def streamTargetContent(vaultId:String, oid:String, request:Request[AnyContent], uid:String) = {
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
          logger.debug(s"maybeResponseSize is $maybeResponseSize")

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

  def loadExistingContent(vaultId: String) = {
    val interestingFields = Array(
      "MXFS_PATH",
      "MXFS_FILENAME",
      "GNM_ASSET_FOLDER",
      "GNM_TYPE",
      "GNM_PROJECT_ID"
    )

    val catchAllSearchTerm = SearchTerm.createNOTTerm(SearchTerm.createSimpleTerm("oid", ""))
    val finalSink = Sink.seq[CachedEntry]
    val content = userInfoCache.infoForVaultId(vaultId)
    //implicit val vault:Vault = MatrixStore.openVault(content.head)
    val graph = GraphDSL.create(finalSink) { implicit builder =>
      sink =>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(new OMFastSearchSource(content.head, Array(catchAllSearchTerm), interestingFields, atOnce = 100))
        src.out.map(elem => {
          //val mxsEntry = vault.getObject(elem.oid)
          //val checkSum = MetadataHelper.getOMFileMd5(mxsEntry) match {
          //  case Failure(err)=>
          //    logger.warn(s"Could not get appliance MD5: ", err)
          //    "None"
          //  case Success(checksum)=>checksum
          //}
          val ent = CachedEntry(
            elem.oid,
            elem.attributes.flatMap(_.stringValues.get("MXFS_PATH")).getOrElse("(no path)"),
            elem.attributes.flatMap(_.stringValues.get("MXFS_FILENAME")).getOrElse("(no filename)"),
            elem.attributes.flatMap(_.stringValues.get("GNM_ASSET_FOLDER")),
            elem.attributes.flatMap(_.stringValues.get("GNM_TYPE")),
            elem.attributes.flatMap(_.stringValues.get("GNM_PROJECT_ID")),
            ""
          )

          logger.debug(s"Got entry $ent")
          ent
        }) ~> sink
        ClosedShape
    }

    RunnableGraph.fromGraph(graph).run()
  }

  case class FullDuplicateData(mxfsPath:String, duplicateNumber:Int, duplicatesData:Seq[CachedEntry])
  case class AllDuplicateData(dupes_count:Int, item_count:Int, duplicates:Array[FullDuplicateData])

  def getDuplicateData(vaultId: String)  = {
    loadExistingContent(vaultId).map(results=>{
      val contentCache = new ExistingArchiveContentCache(results)
      var duplicatesArray: Array[FullDuplicateData] = Array()
      val dupeCount = contentCache.dupesCount
      if (dupeCount > 0) {
        logger.warn(s"There are $dupeCount duplicated files in the archive")
        contentCache.dupedPaths.foreach(dupe => {
          logger.debug(s"${dupe._1}: ${dupe._2} copies")
          val duplicatedItemData = contentCache.getAllForPath(dupe._1)
          val duplicateDataThree = FullDuplicateData(dupe._1,dupe._2,duplicatedItemData)
          duplicatesArray +:= duplicateDataThree
        })
      } else {
        logger.info(s"No duplicates found.")
      }
      logger.info(s"Got existing ${results.length} items in the vault")
      logger.info(s"${duplicatesArray}")
      val fullDuplicateDataTwo = AllDuplicateData(dupes_count = contentCache.dupesCount, item_count = results.length, duplicates = duplicatesArray)
      logger.info(s"${fullDuplicateDataTwo}")
      fullDuplicateDataTwo
    })
  }

  def findDuplicates(vaultId:String) = IsAuthenticatedAsync { uid=> request=>
    getDuplicateData(vaultId).map(result=>{
      Ok(result.asJson)}
    ).recover({
      case _:Throwable=> BadRequest(GenericErrorResponse("error", "An error occurred when attempting to load duplicate data.").asJson)
    })
  }

}