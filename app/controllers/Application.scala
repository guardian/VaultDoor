package controllers

import java.net.URI

import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.stream.scaladsl.{Framing, GraphDSL, Keep, Sink, Source}
import helpers.{ByteBufferSource, OMAccess, OMLocator, RangeHeader, UserInfoCache}
import javax.inject.{Inject, Named, Singleton}
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.http.HttpEntity
import play.api.mvc._
import helpers.BadDataError

import scala.util.{Failure, Success, Try}
import akka.pattern.ask
import akka.stream.{Attributes, Materializer, SourceShape}
import akka.util.ByteString
import com.om.mxs.client.japi.{MatrixStore, UserInfo, Vault}
<<<<<<< HEAD
import models.ObjectMatrixEntry
import streamcomponents.{MatrixStoreFileSourceWithRanges, MultipartSource}
import models.{AuditEvent, AuditFile, ObjectMatrixEntry}
import streamcomponents.MatrixStoreFileSourceWithRanges
=======
import models.{AuditEvent, AuditFile, FileAttributes, ObjectMatrixEntry}
import streamcomponents.{AuditLogFinish, MatrixStoreFileSourceWithRanges}
>>>>>>> working audit output

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import play.api.cache.SyncCacheApi
import auth.Security
import play.api.libs.circe.Circe
import views.html.helper.CSRF


@Singleton
class Application @Inject() (cc:ControllerComponents,
                             config:Configuration,
                             omAccess: OMAccess,
                             @Named("object-cache") objectCache:ActorRef,
                             @Named("audit-actor") auditActor:ActorRef,
                             userInfoCache:UserInfoCache
                            )(implicit mat:Materializer,system:ActorSystem, override implicit val cache:SyncCacheApi)
  extends AbstractController(cc) with Security with Circe {
  import actors.ObjectCache._

  override protected val logger = LoggerFactory.getLogger(getClass)

  private lazy val bufferSize = config.getOptional[Int]("vaults.streamingBufferSize").map(s=>s * 1024*1024).getOrElse(128*1024*1024)

  private implicit val timeout:akka.util.Timeout = 30.seconds

  def index = Action {
    Ok(views.html.index("VaultDoor")("no-cb"))
  }

  /**
    * gathers appropriate headers for the given [[ObjectMatrixEntry]]
    * @param entry [[ObjectMatrixEntry]] instance
    * @return
    */
  def headersForEntry(entry:ObjectMatrixEntry, ranges:Seq[RangeHeader], totalSize:Option[Long]):Map[String,String] = {
    logger.info(entry.attributes.toString)
    val contentRangeHeader = ranges.headOption.map(range=>s"bytes ${range.headerString}${totalSize.map(s=>s"/$s").getOrElse("")}")

    val optionalFields = Seq(
      entry.attributes.flatMap(_.stringValues.get("MXFS_MODIFICATION_TIME")).map(s=>"Etag"->s),
      contentRangeHeader.map(hdr=>"Content-Range"->hdr)
    ).collect({case Some(field)=>field})


    optionalFields.toMap ++ Map(
      "Accept-Ranges"->"bytes",
    )
  }

  def getMaybeResponseSize(entry:ObjectMatrixEntry, overriden:Option[Long]):Option[Long] = {
    overriden match {
      case value @Some(_)=>value
      case None=>entry.fileAttribues.map(_.size)
    }
  }

  def getMaybeMimetype(entry:ObjectMatrixEntry):Option[String] = entry.attributes.flatMap(_.stringValues.get("MXFS_MIMETYPE"))

  def headTargetContent(targetUriString:String) = IsAuthenticatedAsync { uid=> request=>
    val maybeTargetUri = Try {
      URI.create(targetUriString)
    }

    val maybeLocator = maybeTargetUri.flatMap(targetUri => OMLocator.fromUri(targetUri))

    /*
    look up the object, using cache if possible, and get hold of the metadata
     */
    val objectEntryFut = Future.fromTry(maybeLocator).flatMap(locator=>{
      (objectCache ? Lookup(locator)).mapTo[OCMsg].map({
        case ObjectNotFound(_) =>
          val auditFile = AuditFile("",locator.filePath)
          auditActor ! actors.Audit.LogEvent(AuditEvent.NOTFOUND, uid, Some(auditFile), Seq())
          Left(NotFound(s"could not find object $targetUriString")) //FIXME: replace with proper json response
        case ObjectLookupFailed(_, err) =>
          val auditFile = AuditFile("",locator.filePath)
          auditActor ! actors.Audit.LogEvent(AuditEvent.OMERROR, uid, Some(auditFile), Seq(),notes=Some(err.toString))
          logger.error(s"Could not look up object for $targetUriString: ", err)
          Left(InternalServerError(s"lookup failed for $targetUriString"))
        case ObjectFound(_, objectEntry) =>
          val auditFile = AuditFile(objectEntry.oid,locator.filePath)
          auditActor ! actors.Audit.LogEvent(AuditEvent.HEADFILE, uid, Some(auditFile), Seq())
          Right(objectEntry)
      })
    })

    objectEntryFut.map({
      case Left(response)=>response
      case Right(entry)=>

        Result(
          ResponseHeader(200,headersForEntry(entry, Seq(), getMaybeResponseSize(entry, None))),
          HttpEntity.Streamed(Source.empty, getMaybeResponseSize(entry, None), getMaybeMimetype(entry))
        )
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
    * get a ranged file source and link it to an auditing flow, which passes bytes through untouched but counts them and
    * logs start/end to the audit log
    * DEPRECATED DURING MERGE. CONTENT MERGED INTO getStreamingSource
    * @param userInfo UserInfo object describing the appliance and vault to access
    * @param omEntry [[ObjectMatrixEntry]] object describing the file to access
    * @param ranges potentially empty sequence of byte ranges to access. If sequence is empty => whole file
    * @param auditFile [[AuditFile]] object describing the file that is being accessed
    * @param uid user that is making the request
    * @return a partial Graph that emulates a Source.  You can call Source.fromGraph() on this value to get a "real" source back.
    */
  def makeOutputGraph(userInfo:Option[UserInfo],omEntry:ObjectMatrixEntry, ranges:Seq[RangeHeader], auditFile:AuditFile, uid:String) = Try { GraphDSL.create() { implicit builder=>
    import akka.stream.scaladsl.GraphDSL.Implicits._

    val src = builder.add(new MatrixStoreFileSourceWithRanges(userInfo.get, omEntry.oid, omEntry.fileAttribues.get.size,ranges))
    val audit = builder.add(new AuditLogFinish(auditActor,auditFile,uid, omEntry.fileAttribues.get.size))

    src ~> audit

    SourceShape(audit.out)
  }}

  /**
    * third test, use the MatrixStoreFileSourceWithRanges to efficiently stream ranges of content
    * @param targetUriString omms URI of the object that we are trying to get
    * @return
    */
  def streamTargetContent(targetUriString:String) = IsAuthenticatedAsync { uid=> request=>
    val maybeTargetUri = Try {
      URI.create(targetUriString)
    }

    val maybeLocator = maybeTargetUri.flatMap(targetUri => OMLocator.fromUri(targetUri))

    /*
    look up the object, using cache if possible, and get hold of the metadata
     */
    val objectEntryFut = Future.fromTry(maybeLocator).flatMap(locator=>{
      (objectCache ? Lookup(locator)).mapTo[OCMsg].map({
        case ObjectNotFound(_) =>
          val auditFile = AuditFile("",locator.filePath)
          auditActor ! actors.Audit.LogEvent(AuditEvent.NOTFOUND, uid, Some(auditFile), Seq())
          Left(NotFound(s"could not find object $targetUriString")) //FIXME: replace with proper json response
        case ObjectLookupFailed(_, err) =>
          val auditFile = AuditFile("",locator.filePath)
          auditActor ! actors.Audit.LogEvent(AuditEvent.OMERROR, uid, Some(auditFile), Seq(),notes=Some(err.toString))
          logger.error(s"Could not look up object for $targetUriString: ", err)
          Left(InternalServerError(s"lookup failed for $targetUriString"))
        case ObjectFound(_, objectEntry) =>
          Right(objectEntry)
      })
    })

    /*
    break down the ranges header into structured data
     */
    val rangesOrFailureFut = Future.fromTry(request.headers.get("Range") match {
      case Some(hdr)=>RangeHeader.fromStringHeader(hdr)
      case None=>Success(Seq())
    })

    /*
    get hold of a streaming source, if possible
     */
    val srcOrFailureFut = Future.sequence(Seq(objectEntryFut,rangesOrFailureFut)).map(results=>{
      val ranges = results(1).asInstanceOf[Seq[RangeHeader]]

      results.head.asInstanceOf[Either[Result,ObjectMatrixEntry]] match {
        case Right(omEntry)=>
          //maybeLocator.get is safe because if maybeLocator is a Failure we don't execute this block
          val userInfo = userInfoCache.infoForAddress(maybeLocator.get.host,maybeLocator.get.vaultId.toString)

          val responseSize = if(ranges.nonEmpty){
            Some(ranges.foldLeft(0L)((acc,range)=>acc+(range.end.getOrElse(omEntry.fileAttribues.get.size)-range.start.getOrElse(0L))))
          } else {
            omEntry.fileAttribues.map(_.size)
          }

          //log that we are starting a streamout
          val auditFile = AuditFile(omEntry.oid,maybeLocator.get.filePath)
          auditActor ! actors.Audit.LogEvent(AuditEvent.STREAMOUT,uid,Some(auditFile), ranges)

          getStreamingSource(ranges,userInfo.get, omEntry, auditFile, uid) match {
            case Success(partialGraph) =>
              Right((Source.fromGraph(partialGraph), responseSize, headersForEntry(omEntry, ranges, responseSize), getMaybeMimetype(omEntry), ranges.nonEmpty))
            case Failure(err)=> //if we did not get a source, log that
              auditActor ! actors.Audit.LogEvent(AuditEvent.OMERROR, uid, Some(auditFile),ranges, notes=Some(err.getMessage))
              logger.error(s"Could not set up streaming source: ", err)
              Left(InternalServerError(s"Could not set up streaming source, see logs for more details"))
          }

        case Left(err)=> Left(err)
    }})

    srcOrFailureFut.map({
      case Right((byteSource, maybeResponseSize, headers, maybeMimetype, isPartialTransfer)) =>
        logger.info(s"maybeResponseSize is $maybeResponseSize")

        Result(
          ResponseHeader(if(isPartialTransfer) 206 else 200, headers),
          HttpEntity.Streamed(byteSource.log("outputstream").addAttributes(
            Attributes.logLevels(
              onElement = Attributes.LogLevels.Info,
              onFailure = Attributes.LogLevels.Error,
              onFinish = Attributes.LogLevels.Info)), None, maybeMimetype)
        ) //we can't give a proper content length, because if we are sending multipart chunks that adds overhead to the request size.
      case Left(response)=>response
    }).recover({
      case err:BadDataError=>
        BadRequest(err.getMessage)
      case err:Throwable=>
        logger.error(s"Could not get data for $targetUriString: ", err)
        InternalServerError("see the logs for more information")
    })

  }

}