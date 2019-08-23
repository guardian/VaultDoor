package controllers

import java.net.URI

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Framing, GraphDSL, Source}
import helpers.{ByteBufferSource, OMAccess, OMLocator, RangeHeader, UserInfoCache}
import javax.inject.{Inject, Named, Singleton}
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.http.HttpEntity
import play.api.mvc._
import helpers.BadDataError

import scala.util.{Failure, Success, Try}
import akka.pattern.ask
import akka.stream.{Materializer, SourceShape}
import com.om.mxs.client.japi.{MatrixStore, UserInfo, Vault}
import models.ObjectMatrixEntry
import streamcomponents.MatrixStoreFileSourceWithRanges

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.cache.SyncCacheApi
import auth.Security
import views.html.helper.CSRF


@Singleton
class Application @Inject() (cc:ControllerComponents,
                             config:Configuration,
                             omAccess: OMAccess,
                             @Named("object-cache") objectCache:ActorRef,
                             userInfoCache:UserInfoCache
                            )(implicit mat:Materializer,system:ActorSystem, override implicit val cache:SyncCacheApi)
  extends AbstractController(cc) with Security {
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
    * @param maybeResponseSize optional override for content-length. If this is None AND there is no fileattributes metadata
    *                          on `entry` then no Content-Length header is generated
    * @return
    */
  def headersForEntry(entry:ObjectMatrixEntry, maybeResponseSize:Option[Long]):Map[String,String] = {
    val optionalFields = Seq(
      entry.fileAttribues.map(_.size).map(s=>"Content-Length"->s.toString),
      maybeResponseSize.map(s=>"Content-Length"->s.toString),
      entry.attributes.flatMap(_.stringValues.get("MXFS_MODIFICATION_TIME")).map(s=>"Etag"->s)
    ).collect({case Some(field)=>field})

    optionalFields.toMap ++ Map(

    )
  }

  def headTargetContent(targetUriString:String) = Action.async { request=>
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
          Left(NotFound(s"could not find object $targetUriString")) //FIXME: replace with proper json response
        case ObjectLookupFailed(_, err) =>
          logger.error(s"Could not look up object for $targetUriString: ", err)
          Left(InternalServerError(s"lookup failed for $targetUriString"))
        case ObjectFound(_, objectEntry) =>
          Right(objectEntry)
      })
    })



    objectEntryFut.map({
      case Left(response)=>response
      case Right(entry)=>
        Result(
          ResponseHeader(200,headersForEntry(entry, None)),
          HttpEntity.NoEntity
        )
    })
  }
  /**
    * third test, use the MatrixStoreFileSourceWithRanges to efficiently stream ranges of content
    * @param targetUriString omms URI of the object that we are trying to get
    * @return
    */
  def streamTargetContent(targetUriString:String) = Action.async { request:Request[AnyContent]=>
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
          Left(NotFound(s"could not find object $targetUriString")) //FIXME: replace with proper json response
        case ObjectLookupFailed(_, err) =>
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

      results.head.asInstanceOf[Either[Result,(UserInfo,ObjectMatrixEntry)]] match {
        case Right((userInfo, omEntry))=>
          val responseSize = if(ranges.nonEmpty){
            Some(ranges.foldLeft(0L)((acc,range)=>acc+(range.end.getOrElse(omEntry.fileAttribues.get.size)-range.start.getOrElse(0L))))
          } else {
            omEntry.fileAttribues.map(_.size)
          }

          val partialGraph = GraphDSL.create() { implicit builder=>
            val src = builder.add(new MatrixStoreFileSourceWithRanges(userInfo,omEntry.oid, omEntry.fileAttribues.get.size,ranges))

            SourceShape(src.out)
          }

          Right((Source.fromGraph(partialGraph), responseSize, headersForEntry(omEntry, responseSize)))
        case Left(err)=> Left(err)
    }})

    srcOrFailureFut.map({
      case Right((byteSource, maybeResponseSize, headers)) =>

        logger.info(s"maybeResponseSize is $maybeResponseSize")
        Result(
          ResponseHeader(200, headers),
          HttpEntity.Streamed(byteSource, maybeResponseSize, Some("application/octet-stream"))
        )
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