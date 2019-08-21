package controllers

import java.nio.ByteBuffer
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

@Singleton
class Application @Inject() (cc:ControllerComponents,
                             config:Configuration,
                             omAccess: OMAccess,
                             @Named("object-cache") objectCache:ActorRef,
                             userInfoCache:UserInfoCache
                            )(implicit mat:Materializer,system:ActorSystem)
  extends AbstractController(cc) {
  import actors.ObjectCache._

  private val logger = LoggerFactory.getLogger(getClass)

  private lazy val bufferSize = config.getOptional[Int]("vaults.streamingBufferSize").map(s=>s * 1024*1024).getOrElse(128*1024*1024)

  private implicit val timeout:akka.util.Timeout = 30.seconds

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  /**
    * first test (mostly for syntax), get a ByteBuffer and stream it
    * @return
    */
  def test = Action {
    val testbuf  = ByteBuffer.allocate(2048)

    val streamingSource = ByteBufferSource(testbuf, 128)
    Result(
      ResponseHeader(200,Map.empty),
      HttpEntity.Streamed(Source.fromGraph(streamingSource), None, Some("application/octet-stream"))
    )
  }

  def getRange(maybeRangeStr:Option[String]):Try[Seq[RangeHeader]] =
    maybeRangeStr.map(RangeHeader.fromStringHeader) match {
      case Some(Success(result))=>Success(result)
      case None=>Success(Seq())
      case Some(Failure(err))=>Failure(err)
    }


  /**
    * second test, get hold of file locator, pull in a data chunk and stream it
    * @param targetUriString
    * @return
    */
  def test2(targetUriString:String) = Action.async { request =>
    val maybeTargetUri = Try {
      URI.create(targetUriString)
    }

    val maybeLocator = maybeTargetUri.flatMap(targetUri => OMLocator.fromUri(targetUri))

    //get a channel to the file, provided that we have a valid locator and know about the vault.
    //all errors are bundled up into either failure of the Future or an enclosed Left which allows for
    //non-500 error responses
    val maybeChannelFut = Future.fromTry(maybeLocator).flatMap(locator => {
      (objectCache ? Lookup(locator)).mapTo[OCMsg].map({
        case ObjectNotFound(_) =>
          Left(NotFound(s"could not find object $targetUriString")) //FIXME: replace with proper json response
        case ObjectLookupFailed(_, err) =>
          logger.error(s"Could not look up object for $targetUriString: ", err)
          Left(InternalServerError(s"lookup failed for $targetUriString"))
        case ObjectFound(_, oid) =>
          omAccess.getChannel(locator, oid, bufferSize) match {
            case Failure(err) =>
              logger.error(s"Could not open channel to $locator", err)
              Left(InternalServerError(s"Could not open channel to file"))
            case Success(channel) => Right(channel)
          }
      })
    })

    //if we got a channel, pull the data into a buffer and set up a Source to stream it to the client
    val maybeStreamSourceFut = maybeChannelFut.map(_.map(channel => {
      val maybeRange = request.headers.get("Range").map(RangeHeader.fromStringHeader)

      val buf = maybeRange match {
        case Some(Success(range)) =>
          omAccess.loadChunk(channel, range.head.start.get, range.head.end.get)
        case None =>
          omAccess.loadAll(channel) //no range => get the whole damn thing
      }

      if (buf.isFailure) throw buf.failed.get //throw out to the future and it gets caught and returned a 500 error
      (ByteBufferSource(buf.get, 1024 * 1024), buf.get.capacity())
    }))

    //send appropriate responses to the client depending on the status of our operations.
    maybeStreamSourceFut.map({
      case Left(errorResponse)=>errorResponse
      case Right((streamSource,streamLen))=>Result(
        ResponseHeader(200,Map.empty), //FIXME: set correct headers
        HttpEntity.Streamed(Source.fromGraph(streamSource), Some(streamLen), Some("application/octet-stream"))  //FIXME: remove hardcoded mimetype and try to get from appliance
      )
    }).recover({
      case err:Throwable=>
        logger.error(s"Could not get data for $targetUriString: ", err)
        InternalServerError("Could not get data. Consult the logs for more details.")
    })
  }

  /**
    * third test, use the MatrixStoreFileSourceWithRanges to efficiently stream ranges of content
    * @param targetUriString omms URI of the object that we are trying to get
    * @return
    */
  def test3(targetUriString:String) = Action.async { request:Request[AnyContent]=>
    val maybeTargetUri = Try {
      URI.create(targetUriString)
    }

    val maybeLocator = maybeTargetUri.flatMap(targetUri => OMLocator.fromUri(targetUri))

    /*
    look up the object, using cache if possible, and get hold of the metadata
     */
    val objectEntryFut = Future.fromTry(maybeLocator).flatMap(locator=>{
      (objectCache ? Lookup(locator)).mapTo[OCMsg].flatMap({
        case ObjectNotFound(_) =>
          Future(Left(NotFound(s"could not find object $targetUriString"))) //FIXME: replace with proper json response
        case ObjectLookupFailed(_, err) =>
          logger.error(s"Could not look up object for $targetUriString: ", err)
          Future(Left(InternalServerError(s"lookup failed for $targetUriString")))
        case ObjectFound(_, oid) =>
          userInfoCache.infoForAddress(locator.host, locator.vaultId.toString) match {
            case Some(userInfo)=>
              implicit val vault:Vault = MatrixStore.openVault(userInfo)
              ObjectMatrixEntry(oid).getMetadata.andThen({
                case _=>vault.dispose()
              }).map(entry=>Right((userInfo, entry)))
            case None=>
              Future(Left(NotFound(s"no login information for $locator")))
          }
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
          val responseSize = ranges.foldLeft(0L)((acc,range)=>acc+(range.end.getOrElse(omEntry.fileAttribues.get.size)-range.start.getOrElse(0L)))

          val partialGraph = GraphDSL.create() { implicit builder=>
            val src = builder.add(new MatrixStoreFileSourceWithRanges(userInfo,omEntry.oid, omEntry.fileAttribues.get.size,ranges))

            SourceShape(src.out)
          }

          Right((Source.fromGraph(partialGraph), responseSize))
        case Left(err)=> Left(err)
    }})

    srcOrFailureFut.map({
      case Right((byteSource, responseSize)) =>
        val maybeResponseSize = if(responseSize>0) Some(responseSize) else None

        Result(
          ResponseHeader(200, Map.empty), //FIXME: set correct header
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