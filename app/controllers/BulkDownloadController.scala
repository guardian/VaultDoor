package controllers

import java.time.{Instant, ZonedDateTime}
import akka.actor.ActorSystem
import akka.stream.{ClosedShape, Materializer, SourceShape}
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink, Source}
import auth.{BearerTokenAuth, Security}
import com.om.mxs.client.japi.{MatrixStore, SearchTerm, UserInfo, Vault}
import helpers.{MetadataHelper, SearchTermHelper, UserInfoCache}

import javax.inject.{Inject, Singleton}
import models.{ArchiveEntryDownloadSynopsis, LightboxBulkEntry, ObjectMatrixEntry, ServerTokenDAO, ServerTokenEntry}
import play.api.Configuration
import play.api.libs.circe.Circe
import play.api.mvc.{AbstractController, AnyContent, ControllerComponents, Request, ResponseHeader, Result}
import responses.{BulkDownloadInitiateResponse, DownloadManagerItemResponse, GenericErrorResponse, GenericObjectResponse}
import io.circe.syntax._
import io.circe.generic.auto._
import play.api.cache.SyncCacheApi
import play.api.http.HttpEntity
import streamcomponents.{MakeDownloadSynopsis, MatrixStoreFileSourceWithRanges, OMFastContentSearchSource, OMFastSearchSource}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Singleton
class BulkDownloadController @Inject() (cc:ControllerComponents,
                                        override implicit val config:Configuration,
                                        override val bearerTokenAuth:BearerTokenAuth,
                                        serverTokenDAO: ServerTokenDAO,
                                        userInfoCache: UserInfoCache)
                                       (implicit mat:Materializer, system:ActorSystem, override implicit val cache:SyncCacheApi)
  extends AbstractController(cc) with Circe with Security with ObjectMatrixEntryMixin{

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
    case None=>Future(NotFound(GenericErrorResponse("not_found",vaultId).asJson))
  }

  /**
    * called from a logged-in browser session to initiate the download session.
    * Generates a one-time token tied to the user, project and vault, saves it and returns it to the frontend.
    * No search or validation is done on the vault or project IDs at this point.
    * It's assumed that the storage backend takes care of expiry
    * @param vaultId Vault ID that is being targeted
    * @param projectId Project ID that is being targeted
    * @return a Play response
    */
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

  /**
    * performs a streaming search against the ObjectMatrix vault and convert the returned results to
    * ArchiveEntryDownloadSynopsis objects for sending to Download Manager
    * @param userInfo UserInfo instance indicating the appliance and vault to target
    * @param projectId project ID to search for
    * @return an ArchiveEntryDownloadSynopsis object for each file of the project, returned in a Future
    */
  def getContent(userInfo:UserInfo, projectId:String) = {
    val usefulFields = Array("MXFS_PATH","MXFS_FILENAME","DPSP_SIZE")

    SearchTermHelper.projectIdQuery(projectId) match {
      case Some(projectQuery) =>
        val sinkFact = Sink.seq[ArchiveEntryDownloadSynopsis]
        val graph = GraphDSL.create(sinkFact) { implicit builder =>
          sink =>
            import akka.stream.scaladsl.GraphDSL.Implicits._

            val src = builder.add(new OMFastContentSearchSource(userInfo, projectQuery.withKeywords(usefulFields).build))
            val converter = builder.add(new MakeDownloadSynopsis(config.getOptional[Seq[String]]("bulkDownload.stripPrefixes")))
            src ~> converter ~> sink
            ClosedShape
        }

        RunnableGraph.fromGraph(graph).run().map(Right.apply)
      case None =>
        Future(Left("project ID is invalid"))
    }
  }

  /**
    * create and save a long-term token to allow the download of items
    * @param uid user ID creating the token
    * @param combinedId combined vault and project ID string
    * @return the saved ServerTokenEntry in a Future
    */
  def createRetrievalToken(uid:String, combinedId:String) = {
    val expiry = config.getOptional[Int]("serverToken.longLivedDuration").getOrElse(3600)
    val newToken = ServerTokenEntry.create(Some(combinedId), expiry, Some(uid))
    serverTokenDAO.put(newToken, expiry).map(_=>newToken)
  }

  /**
    * handle the return of a short-lived token. Validate it and if it passes delete it, then send back a long-lived
    * token and a list of items to download.
    * @param tokenId the ID of a short-lived token
    * @return
    */
  def getBulkDownload(tokenId:String) = Action.async {
    serverTokenDAO.get(tokenId).flatMap({
      case None=>
        Future(NotFound(GenericErrorResponse("not_found","No such bulk download").asJson))
      case Some(token)=>
        serverTokenDAO.remove(tokenId).flatMap(_=> {
        token.associatedId match {
          case None=>
            logger.error(s"Token $tokenId is invalid, it does not contain a project ID")
            Future(NotFound(GenericErrorResponse("not_found","Invalid token").asJson))
          case Some(combinedId)=>
            val ids = combinedId.split("\\|")
            val projectId = ids.head
            val vaultId = ids(1)

            logger.debug(s"Combined ID is $combinedId, project ID is $projectId, vault ID is $vaultId")

            withVaultAsync(vaultId) { userInfo =>
              createRetrievalToken(token.createdForUser.getOrElse(""), combinedId).flatMap(retrievalToken=> {
                getContent(userInfo, projectId).map({
                  case Right(synopses)=>
                    val meta = LightboxBulkEntry(projectId, s"Vaultdoor download for project $projectId", token.createdForUser.getOrElse(""), ZonedDateTime.now(), 0, synopses.length, 0)
                    Ok(BulkDownloadInitiateResponse("ok", meta, retrievalToken.value, synopses).asJson)
                  case Left(problem)=>
                    logger.warn(s"Could not complete bulk download for token $tokenId: $problem")
                    BadRequest(GenericErrorResponse("invalid", problem).asJson)
                })
              })
            }
          }
        }).recover({
          case err:Throwable=>
            logger.error(s"Could not get bulk download for token $tokenId: ", err)
            InternalServerError(GenericErrorResponse("error","Server failure, please check the logs").asJson)
        })
    })
  }

  def eitherOr[T](opt1:Option[T],opt2:Option[T]):Option[T] = {
    if(opt1.isDefined){
      opt1
    } else {
      opt2
    }
  }

  /**
    * validates that there is a token in the headers of the given request and that it is valid.
    * if so, pass it on to the provided Block and return the result. Otherwise return a Play response indicating the error
    * @param request request object
    * @param block function to process the request if the token is valid.
    *              Takes three parameters, being the entire ServerTokenEntry, the project ID and the vault ID as encoded in the ServerTokenEntry
    * @return the result of the Block or a JSON formatted error response
    */
  def validateTokenAsync(request:Request[AnyContent], actualTokenValue:Option[String])(block: (ServerTokenEntry,String,String)=>Future[Result]):Future[Result] = {
    eitherOr(actualTokenValue, request.headers.get("X-Download-Token")) match {
      case None=>
        logger.error(s"Attempt to download with no X-Download-Token header")
        Future(BadRequest(GenericErrorResponse("bad_request","No download token in headers").asJson))
      case Some(tokenId)=>
        logger.debug(s"Got token ID $tokenId")
        serverTokenDAO.get(tokenId).flatMap({
          case Some(serverToken)=>
            logger.debug(s"Got server token $serverToken for $tokenId")
            val updatedServerToken = serverToken.copy(uses=serverToken.uses+1)
            val expirySeconds = serverToken.expiry.get.toInstant.getEpochSecond - Instant.now().getEpochSecond
          if(expirySeconds<1){
              logger.error(s"Server token $serverToken is expired")
              Future(BadRequest(GenericErrorResponse("token_error","Expired token").asJson))
            } else {
              serverTokenDAO.put(updatedServerToken, expirySeconds.toInt).flatMap(_ => {
                val idSplit = serverToken.associatedId.get.split("\\|")
                block(serverToken, idSplit.head, idSplit(1))
              })
            }
          case None=>
            Future(NotFound(GenericErrorResponse("not_found","item was not found").asJson))
        })
    }
  }

  def getStreamingSourceFor(userInfo:UserInfo, omEntry:ObjectMatrixEntry) = {
    Source.fromGraph(GraphDSL.create() { implicit builder =>
      val src = builder.add(new MatrixStoreFileSourceWithRanges(userInfo,omEntry.oid,omEntry.fileAttribues.get.size,Seq()))
      SourceShape(src.out)
    })
  }

  def getMaybeResponseSize(entry:ObjectMatrixEntry, overriden:Option[Long]):Option[Long] = {
    overriden match {
      case value @Some(_)=>value
      case None=>entry.fileAttribues.map(_.size)
    }
  }

  def getMaybeMimetype(entry:ObjectMatrixEntry):Option[String] = entry.attributes.flatMap(_.stringValues.get("MXFS_MIMETYPE"))

  def bulkDownloadItem(tokenValue:String, itemId:String) = Action {
    //in ArchiveHunter this step is necessary to compute a presigned URL. Here it's not, we can stream the data right away.
    //but to keep the same protocol, we just send back the "right" URL for the data and a flag saying it's available.
    Ok(DownloadManagerItemResponse("ok","RS_ALREADY",s"/api/bulk/$tokenValue/get/$itemId/data").asJson)
  }

  def bulkDownloadItemData(tokenValue:String, itemId:String) = Action.async { request=>
    logger.warn(s"bulkDownloadItem: token is $tokenValue, item ID is $itemId")
    validateTokenAsync(request, Some(tokenValue)) { (serverToken, projectId, vaultId)=>
      logger.debug(s"In bulkDownloadItem for $itemId on vault $vaultId")

      userInfoCache.infoForVaultId(vaultId) match {
        case None=>
          logger.error(s"bulkDownloadItem - vaultId is not valid, had no userInfoCache entry")
          Future(NotFound(GenericErrorResponse("not_found","item or vault not found").asJson))
        case Some(userInfo)=>
          implicit val vault:Vault = MatrixStore.openVault(userInfo)
          ObjectMatrixEntry(itemId).getMetadata.map(entry=>{
            entry.attributes.flatMap(_.stringValues.get("GNM_PROJECT_ID")) match {
              case None=>
                logger.error(s"Item $itemId found in vault $vaultId but it is not a member of any project!")
                vault.dispose()
                NotFound(GenericErrorResponse("not_found","item or vault not found").asJson)
              case Some(itemsProjectId)=>
                if(projectId!=itemsProjectId) {
                  logger.error(s"Item $itemId found in vault $vaultId but it is a member of project $itemsProjectId not $projectId")
                  vault.dispose()
                  NotFound(GenericErrorResponse("not_found","item or vault not found").asJson)
                } else {
                  val headers = headersForEntry(entry, Seq(), getMaybeResponseSize(entry, None))
                  val mxsEntry = vault.getObject(itemId)
                  val updatedHeaders = MetadataHelper.getOMFileMd5(mxsEntry) match {
                    case Failure(err)=>
                      logger.warn(s"Could not get appliance MD5: ", err)
                      headers
                    case Success(checksum)=>headers + ("ETag"->checksum)
                  }

                  vault.dispose()
                  Result(
                    ResponseHeader(200, updatedHeaders),
                    HttpEntity.Streamed(getStreamingSourceFor(userInfo, entry), getMaybeResponseSize(entry, None), getMaybeMimetype(entry))
                  )
                }
            }
          }).recover({
            case err:Throwable=>
              logger.error(s"Could not get metadata for $itemId: ", err)
              vault.dispose()
              InternalServerError(GenericErrorResponse("sever_error","Could not get metadata").asJson)
          })
      }
    }
  }
}
