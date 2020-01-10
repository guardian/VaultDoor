package controllers

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.stream.{ClosedShape, Materializer}
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink}
import auth.Security
import com.om.mxs.client.japi.{SearchTerm, UserInfo}
import helpers.UserInfoCache
import javax.inject.Inject
import models.{ArchiveEntryDownloadSynopsis, LightboxBulkEntry, ServerTokenDAO, ServerTokenEntry}
import play.api.Configuration
import play.api.libs.circe.Circe
import play.api.mvc.{AbstractController, ControllerComponents, Result}
import responses.{BulkDownloadInitiateResponse, GenericErrorResponse, GenericObjectResponse}
import io.circe.syntax._
import io.circe.generic.auto._
import play.api.cache.SyncCacheApi
import streamcomponents.MakeDownloadSynopsis

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BulkDownloadController @Inject() (cc:ControllerComponents, config:Configuration, serverTokenDAO: ServerTokenDAO, userInfoCache: UserInfoCache)
                                       (implicit mat:Materializer, system:ActorSystem, override implicit val cache:SyncCacheApi)
  extends AbstractController(cc) with Circe with Security {

  /**
    * looks up the given vault ID in the UserInfoCache and calls the provided block with the resolved UserInfo object.
    * The block is expected to return a Play Result object and this is passed back as the ultimate result
    * If the vault ID cannot be found then a NotFound response is returned and the block is not called
    * @param vaultId vault ID to query
    * @param block a block that takes a single UserInfo argument and returns a Result
    * @return a Play Result, either the return value of the block of a NotFound
    */
  def withVault(vaultId:String)(block:UserInfo=>Result) = userInfoCache.infoForVaultId(vaultId) match {
    case Some(userInfo)=>block(userInfo)
    case None=>NotFound(GenericErrorResponse("not_found","").asJson)
  }

  def withVaultAsync(vaultId:String)(block:UserInfo=>Future[Result]) = userInfoCache.infoForVaultId(vaultId) match {
    case Some(userInfo)=>block(userInfo)
    case None=>Future(NotFound(GenericErrorResponse("not_found","").asJson))
  }

  def initiate(vaultId:String,projectId:String) = IsAuthenticatedAsync { uid=> request=>
    val expiry = config.getOptional[Int]("serverToken.shortLivedDuration").getOrElse(10)
    val newToken = ServerTokenEntry.create(Some(projectId + "|" + vaultId), expiry, Some(uid))
    serverTokenDAO.put(newToken, expiry)
      .map(_=>Ok(GenericObjectResponse("ok","link",s"archivehunter:vaultdownload:${newToken.value}").asJson))
      .recover({
        case err:Throwable=>
          logger.error(s"Could not save token with id ${newToken.value} for project ${projectId} to backend: ",err)
          InternalServerError(GenericErrorResponse("db_error","Could not save token").asJson)
      })
  }

  def getContent(userInfo:UserInfo, projectId:String) = {
    val searchTerms = Array(SearchTerm.createSimpleTerm("GNM_PROJECT_ID", projectId))
    val usefulFields = Array("MXFS_PATH","MXFS_FILENAME","DPSP_SIZE")
    val sinkFact = Sink.seq[ArchiveEntryDownloadSynopsis]
    val graph = GraphDSL.create(sinkFact) { implicit builder=> sink=>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val src = builder.add(new OMFastSearchSource(userInfo, searchTerms, usefulFields))
      val converter = builder.add(new MakeDownloadSynopsis)
      src ~> converter ~> sink
      ClosedShape
    }

    RunnableGraph.fromGraph(graph).run()
  }

  def createRetrievalToken(uid:String, combinedId:String) = {
    val expiry = config.getOptional[Int]("serverToken.longLivedDuration").getOrElse(3600)
    val newToken = ServerTokenEntry.create(Some(combinedId), expiry, Some(uid))
    serverTokenDAO.put(newToken, expiry).map(_=>newToken)
  }

  def getBulkDownload(tokenId:String) = Action.async {
    serverTokenDAO.get(tokenId).flatMap({
      case None=>
        Future(NotFound(GenericErrorResponse("not_found","No such bulk download").asJson))
      case Some(token)=>
        token.associatedId match {
          case None=>
            logger.error(s"Token $tokenId is invalid, it does not contain a project ID")
            Future(NotFound(GenericErrorResponse("not_found","Invalid token").asJson))
          case Some(combinedId)=>
            serverTokenDAO.remove(tokenId).flatMap(_=> {
              val ids = combinedId.split("|")
              val projectId = ids.head
              val vaultId = ids(1)

              withVaultAsync(vaultId) { userInfo =>
                getContent(userInfo, projectId).map(synopses => {
                  val meta = LightboxBulkEntry(projectId, "", token.createdForUser.getOrElse(""), ZonedDateTime.now(), 0, synopses.length, 0)
                  BulkDownloadInitiateResponse("ok", meta, retrievalToken, synopses)
                })
              }
            })
        }
    })
  }
}
