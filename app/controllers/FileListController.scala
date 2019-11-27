package controllers

import akka.actor.ActorSystem
import akka.stream.{Materializer, SourceShape}
import akka.stream.scaladsl.{GraphDSL, Source}
import akka.util.ByteString
import auth.Security
import com.om.mxs.client.japi.{Attribute, Constants, SearchTerm}
import helpers.{OMAccess, OMLocator, UserInfoCache, ZonedDateTimeEncoder}
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.circe.Circe
import play.api.mvc.{AbstractController, ControllerComponents, ResponseHeader, Result}
import responses.GenericErrorResponse
import streamcomponents.{OMLookupMetadata, OMSearchSource}
import io.circe.syntax._
import io.circe.generic.auto._
import models.PresentableFile
import org.slf4j.LoggerFactory
import play.api.cache.SyncCacheApi
import play.api.http.HttpEntity

import scala.concurrent.ExecutionContext

@Singleton
class FileListController @Inject() (cc:ControllerComponents,
                                    config:Configuration,
                                    omAccess: OMAccess,
                                    userInfoCache: UserInfoCache
                                   )(implicit mat:Materializer, system:ActorSystem, override implicit val cache:SyncCacheApi)
  extends AbstractController(cc) with Security with ObjectMatrixEntryMixin with Circe with ZonedDateTimeEncoder{

  override protected val logger = LoggerFactory.getLogger(getClass)

  def pathSearchStreaming(vaultId:String, forPath:Option[String]) = IsAuthenticated { uid=> request=>
    logger.warn(s"pathSeearchStreaming: $vaultId $forPath")
    userInfoCache.infoForVaultId(vaultId) match {
      case Some(userInfo) =>
        implicit val ec: ExecutionContext = system.dispatcher
        val searchAttrib = forPath match {
          case Some(searchPath)=>new Attribute(Constants.CONTENT, s"""MXFS_FILENAME:"$searchPath"""" )
          case None=>new Attribute(Constants.CONTENT, s"*")
        }

        val graph = GraphDSL.create() { implicit builder =>
          import akka.stream.scaladsl.GraphDSL.Implicits._

          val src = builder.add(new OMSearchSource(userInfo, Some(SearchTerm.createSimpleTerm(searchAttrib)), None))
          val lookup = builder.add(new OMLookupMetadata(userInfo).async)

          src ~> lookup

          val outlet = lookup.out
            .map(PresentableFile.fromObjectMatrixEntry)
            .map(_.asJson.noSpaces)
            .map(jsonString => ByteString(jsonString + "\n"))
            .outlet
          SourceShape(outlet)
        }

        Result(
          ResponseHeader(200, Map()),
          HttpEntity.Streamed(Source.fromGraph(graph), None, Some("application/x-ndjson"))
        )
      case None =>
        NotFound(GenericErrorResponse("not_found", "").asJson)
    }
  }
}
