package controllers

import java.nio.ByteBuffer
import java.net.URI

import akka.actor.ActorRef
import akka.stream.scaladsl.{Framing, Source}
import helpers.{ByteBufferSource, OMAccess, OMLocator, RangeHeader}
import javax.inject.{Inject, Named, Singleton}
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.http.HttpEntity
import play.api.mvc._

import scala.util.{Failure, Success, Try}
import akka.pattern.ask

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class Application @Inject() (cc:ControllerComponents,
                             config:Configuration,
                             omAccess: OMAccess,
                             @Named("object-cache")objectCache:ActorRef
                            )
  extends AbstractController(cc) {
  import actors.ObjectCache._

  private val logger = LoggerFactory.getLogger(getClass)

  private lazy val bufferSize = config.getOptional[Int]("vaults.streamingBufferSize").map(s=>s * 1024*1024).getOrElse(128*1024*1024)

  private implicit val timeout:akka.util.Timeout = 30.seconds

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def test = Action {
    val testbuf  = ByteBuffer.allocate(2048)

    val streamingSource = ByteBufferSource(testbuf, 128)
    Result(
      ResponseHeader(200,Map.empty),
      HttpEntity.Streamed(Source.fromGraph(streamingSource), None, Some("application/octet-stream"))
    )
  }

  def getRange(maybeRangeStr:Option[String]):Try[Option[RangeHeader]] =
    maybeRangeStr.map(RangeHeader.fromStringHeader) match {
      case Some(Success(result))=>Success(Some(result))
      case None=>Success(None)
      case Some(Failure(err))=>Failure(err)
    }


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

    val maybeStreamSourceFut = maybeChannelFut.map(_.map(channel => {

      val maybeRange = request.headers.get("Range").map(RangeHeader.fromStringHeader)

      val buf = maybeRange match {
        case Some(Success(range)) =>
          omAccess.loadChunk(channel, range.start, range.end)
        case None =>
          omAccess.loadAll(channel)
      }

      if (buf.isFailure) throw buf.failed.get //throw out to the future and it gets caught ass a 500 error

      (ByteBufferSource(buf.get, 1024 * 1024), buf.get.capacity())
    }))

    maybeStreamSourceFut.map({
      case Left(errorResponse)=>errorResponse
      case Right((streamSource,streamLen))=>Result(
        ResponseHeader(200,Map.empty), //FIXME: set correct headers
        HttpEntity.Streamed(Source.fromGraph(streamSource), Some(streamLen), Some("application/octet-stream"))  //FIXME: remove hardcoded mimetype and try to get from appliance
      )
    })
  }

}