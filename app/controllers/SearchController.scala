package controllers

import akka.actor.ActorSystem
import akka.stream.{Materializer, SourceShape}
import akka.stream.scaladsl.{Balance, Framing, GraphDSL, Merge, Source}
import auth.Security
import com.om.mxs.client.SearchTerm
import com.om.mxs.client.japi.{Attribute, Constants, UserInfo}
import helpers.{UserInfoCache, ZonedDateTimeEncoder}
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.http.HttpEntity
import play.api.mvc.{AbstractController, ControllerComponents, ResponseHeader, Result}
import io.circe.generic.auto._
import io.circe.syntax._
import models.ObjectMatrixEntry
import play.api.cache.SyncCacheApi
import play.api.libs.circe.Circe
import requests.SearchRequest
import responses.{FileListEntry, GenericErrorResponse, ObjectListResponse}
import streamcomponents.{OMLookupMetadata, OMSearchSource}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SearchController @Inject() (config:Configuration, cc:ControllerComponents, userInfoCache:UserInfoCache)
                                 (override implicit val cache:SyncCacheApi, implicit val ec:ExecutionContext, implicit val actorSystem:ActorSystem, implicit val mat:Materializer)
  extends AbstractController(cc) with Security with Circe with ZonedDateTimeEncoder {

  def vaultsList() = IsAuthenticated { uid=> request=>
    val userInfos = userInfoCache.getAll()
    val results = userInfos.map(userInfo=>Map("host"->userInfo.getAddresses.toSeq, "vaultid"->Seq(userInfo.getVault)))
    Ok(ObjectListResponse("ok",results,None).asJson)
  }

  def buildSearchGraph(userInfo:UserInfo, search:Attribute, startAt:Long, limit:Long) = {
    val paralellism = config.getOptional[Int]("search.lookup_parallel").getOrElse(2)

    GraphDSL.create() { implicit builder=>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val src = builder.add(new OMSearchSource(userInfo,None,Some(search)).async)

      val lookupFactory = new OMLookupMetadata(userInfo)

        val balancer = builder.add(Balance[ObjectMatrixEntry](paralellism))
        val merger = builder.add(Merge[ObjectMatrixEntry](paralellism))

        src ~> balancer.in
        for (i<-0 until paralellism) {
          val lookup = builder.add(lookupFactory)
          balancer.out(i) ~> lookup
          lookup ~> merger.in(i)
        }

      //FIXME: remove the "GET" and handle this better
      SourceShape(merger.out.drop(startAt).take(limit).map(elem=>FileListEntry.fromObjectMatrixEntry(elem).get.asJson.noSpaces + "\n").outlet)
    }
  }

  def searchVault(start:Option[Long], limit:Option[Long]) = IsAuthenticatedAsync(circe.json) { uid =>
    request =>
      request.body.as[SearchRequest] match {
        case Left(parseError) => Future(BadRequest(GenericErrorResponse("parse", parseError.toString).asJson))
        case Right(req) =>
          val search = req.fileName match {
            case Some(fileName) => new Attribute(Constants.CONTENT, s"""MXFS_FILENAME:"$fileName"""")
            case None => new Attribute(Constants.CONTENT, s"*")
          }

          userInfoCache.infoForAddress(req.anyHost, req.vaultId) match {
            case None=>
              Future(NotFound(GenericErrorResponse("not_found","No vault with that ID known on that appliance").asJson))
            case Some(userInfo)=>
              val streamSrc = Source.fromGraph(buildSearchGraph(userInfo, search, start.getOrElse(0L), limit.getOrElse(1000L)))

              Future(Ok.streamed(streamSrc,None,Some("application/x-ndjson")))
          }

      }
  }
}
