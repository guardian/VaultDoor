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
    Ok(views.html.index("VaultDoor")("no-cb"))
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
          val responseSize = if(ranges.nonEmpty){
            ranges.foldLeft(0L)((acc,range)=>acc+(range.end.getOrElse(omEntry.fileAttribues.get.size)-range.start.getOrElse(0L)))
          } else {
            omEntry.fileAttribues.map(_.size).getOrElse(0L)
          }

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

        logger.info(s"maybeResponseSize is $maybeResponseSize")
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