package controllers

import akka.actor.ActorSystem
import akka.stream.{Materializer, SourceShape}
import akka.stream.scaladsl.{GraphDSL, Source}
import akka.util.ByteString
import auth.Security
import com.om.mxs.client.japi.{Attribute, Constants, SearchTerm, UserInfo, Vault}
import helpers.{OMAccess, OMLocator, UserInfoCache, ZonedDateTimeEncoder}
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.circe.Circe
import play.api.mvc.{AbstractController, ControllerComponents, EssentialAction, ResponseHeader, Result}
import responses.GenericErrorResponse
import streamcomponents.{OMLookupMetadata, OMSearchSource}
import io.circe.syntax._
import io.circe.generic.auto._
import models.{MxsMetadata, PresentableFile}
import org.slf4j.LoggerFactory
import play.api.cache.SyncCacheApi
import play.api.http.HttpEntity
import io.circe.generic.auto._
import io.circe.syntax._
import requests.SearchRequest

@Singleton
class FileListController @Inject() (cc:ControllerComponents,
                                    config:Configuration,
                                    omAccess: OMAccess,
                                    userInfoCache: UserInfoCache
                                   )(implicit mat:Materializer, system:ActorSystem, override implicit val cache:SyncCacheApi)
  extends AbstractController(cc) with Security with ObjectMatrixEntryMixin with Circe with ZonedDateTimeEncoder{

  override protected val logger = LoggerFactory.getLogger(getClass)

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

  /**
    * perform the search given by `searchTerm` against the vault indicated in UserInfo, look up the metadata for each
    * file and return the output as a stream of NDJSON
    * @param userInfo UserInfo object indicating the appliance address, login credentials and vault
    * @param searchTerm a SearchTerm object representing the search. Create this with the MXS SDK SearchTerm.create*Term
    *                   methods
    * @return a Play streaming response
    */
  def searchGraph(userInfo:UserInfo, searchTerm:SearchTerm) = {
    val graph = GraphDSL.create() { implicit builder =>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val src = builder.add(new OMSearchSource(userInfo, Some(searchTerm), None))
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
  }

  /**
    * endpoint to perform a search against the Project ID field
    * @param vaultId
    * @param forProject
    * @return
    */
  def projectSearchStreaming(vaultId:String, forProject:String) = IsAuthenticated { uid=> request=>
    withVault(vaultId) { userInfo=>
      val searchAttrib = new Attribute(Constants.CONTENT, s"""GNM_PROJECT_ID:"$forProject"""")
      searchGraph(userInfo, SearchTerm.createSimpleTerm(searchAttrib))
    }
  }

  /**
    * endpoint to perform a search against the filename
    * @param vaultId
    * @param forPath
    * @return
    */
  def pathSearchStreaming(vaultId:String, forPath:Option[String]) = IsAuthenticated { uid=> request=>
    userInfoCache.infoForVaultId(vaultId) match {
      case Some(userInfo) =>
        //implicit val ec: ExecutionContext = system.dispatcher
        val searchAttrib = forPath match {
          case Some(searchPath)=>new Attribute(Constants.CONTENT, s"""MXFS_FILENAME:"$searchPath"""" )
          case None=>new Attribute(Constants.CONTENT, s"*")
        }

        searchGraph(userInfo, SearchTerm.createSimpleTerm(searchAttrib))
      case None =>
        NotFound(GenericErrorResponse("not_found", "").asJson)
    }
  }

  /**
    * endpoint to perform a generic search based on a JSON payload containing a CustomMXSMetadata object
    * @param vaultId vault ID to search. This must exist in the UserInfoCache.
    * @return streaming response of NDJSON ByteStrings
    */
  def customSearchStreaming(vaultId:String) = IsAuthenticated(circe.json[SearchRequest]) { uid=> request=>
    val searchParams = request.body.meta
    val terms = searchParams.toAttributes(MxsMetadata.empty).toAttributes.map(SearchTerm.createSimpleTerm)
    val combinedTerm = SearchTerm.createANDTerm(terms.toArray)
    withVault(vaultId) { userInfo=>
      searchGraph(userInfo, combinedTerm)
    }
  }
}
