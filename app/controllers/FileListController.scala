package controllers

import akka.actor.ActorSystem
import akka.stream.{ClosedShape, Materializer, SourceShape}
import akka.stream.scaladsl.{GraphDSL, Keep, RunnableGraph, Source}
import akka.util.ByteString
import auth.{BearerTokenAuth, Security}
import com.om.mxs.client.japi.{Attribute, Constants, SearchTerm, UserInfo, Vault}
import helpers.{UserInfoCache, ZonedDateTimeEncoder}
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.circe.Circe
import play.api.mvc.{AbstractController, ControllerComponents, EssentialAction, ResponseHeader, Result}
import responses.GenericErrorResponse
import streamcomponents.{OMFastSearchSource, OMLookupMetadata, OMSearchSource, ProjectSummarySink}
import models.{MxsMetadata, PresentableFile, ProjectSummary, ProjectSummaryEncoder, SummaryEntry}
import org.slf4j.LoggerFactory
import play.api.cache.SyncCacheApi
import play.api.http.HttpEntity
import requests.SearchRequest

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.generic.semiauto._

@Singleton
class FileListController @Inject() (cc:ControllerComponents,
                                    override implicit val config:Configuration,
                                    override val bearerTokenAuth:BearerTokenAuth,
                                    userInfoCache: UserInfoCache
                                   )(implicit mat:Materializer, system:ActorSystem, override implicit val cache:SyncCacheApi)
  extends AbstractController(cc) with Security with ObjectMatrixEntryMixin with Circe with ZonedDateTimeEncoder with ProjectSummaryEncoder {

  override protected val logger = LoggerFactory.getLogger(getClass)

  implicit val SummaryEntryEncoder = deriveEncoder[SummaryEntry]
  implicit val ProjectSummaryEncoder = deriveEncoder[ProjectSummary]

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

  /**
    * perform the search given by `searchTerm` against the vault indicated in UserInfo, look up the metadata for each
    * file and return the output as a stream of NDJSON
    * @param userInfo UserInfo object indicating the appliance address, login credentials and vault
    * @param searchTerm a SearchTerm object representing the search. Create this with the MXS SDK SearchTerm.create*Term
    *                   methods
    * @return an Akka streams graph that yields NDJSON elements
    */
  def searchGraph(userInfo:UserInfo, searchTerm:SearchTerm) =
    GraphDSL.create() { implicit builder =>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      //val src = builder.add(new OMSearchSource(userInfo, Some(searchTerm), None))
      val src = builder.add(new OMFastSearchSource(userInfo, Array(searchTerm),Array()))
      val lookup = builder.add(new OMLookupMetadata(userInfo).async)

      src ~> lookup

      val outlet = lookup.out
        .log("FileListController.searchGraph")
        .map(PresentableFile.fromObjectMatrixEntry)
        .collect({case Some(presentableFile)=>presentableFile})
        .map(elem=>{
          try {
            elem.asJson.noSpaces
          } catch {
            case err:Throwable=>
              logger.error(s"json conversion for ${elem.oid} failed: ${err.getMessage}", err)
              throw err
          }
        })
        .map(jsonString => ByteString(jsonString + "\n"))
        .outlet
      SourceShape(outlet)
    }

  /**
    * runs a graph to determine ProjectSummary information for the given search term in the given vault
    * @param userInfo
    * @param searchTerm
    * @return
    */
  def summaryFor(userInfo:UserInfo, searchTerm:SearchTerm) = {
    val sinkFact = new ProjectSummarySink

    ProjectSummarySink.suitableFastSource(userInfo,Array(searchTerm)).toMat(sinkFact)(Keep.right).run()
  }

  def vaultSummary(vaultId:String) = IsAuthenticatedAsync { uid=> request=>
    withVaultAsync(vaultId) { userInfo=>
      val t = SearchTerm.createSimpleTerm(new Attribute(Constants.CONTENT, s"*"))
      summaryFor(userInfo,t).map(summary=>{
        Ok(summary.asJson)
      })
    }
  }

  def projectsummary(vaultId:String, forProject:String) = IsAuthenticatedAsync { uid => request =>
    withVaultAsync(vaultId) { userInfo=>
      logger.info(s"projectsummary: looking up '$forProject' on $vaultId (${userInfo.getVault}")
      //val t = SearchTerm.createSimpleTerm("GNM_PROJECT_ID", forProject)
      val t = SearchTerm.createSimpleTerm(Constants.CONTENT, s"""GNM_PROJECT_ID:"$forProject""")
      summaryFor(userInfo, t).map(summary=>{
        Ok(summary.asJson)
      })
    }
  }

  /**
    * endpoint to perform a search against the Project ID field
    * @param vaultId
    * @param forProject
    * @return
    */
  def projectSearchStreaming(vaultId:String, forProject:String) = IsAuthenticated { uid=> request=>
    withVault(vaultId) { userInfo=>
      //val searchAttrib = new Attribute("GNM_PROJECT_ID", forProject)//s"""GNM_PROJECT_ID:"$forProject"""")
      val t = SearchTerm.createSimpleTerm(Constants.CONTENT, s"""GNM_PROJECT_ID:"$forProject""")
      val graph = searchGraph(userInfo, t)

      Result(
        ResponseHeader(200, Map()),
        HttpEntity.Streamed(Source.fromGraph(graph), None, Some("application/x-ndjson"))
      )
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

        val graph = searchGraph(userInfo, SearchTerm.createSimpleTerm(searchAttrib))

        Result(
          ResponseHeader(200, Map()),
          HttpEntity.Streamed(Source.fromGraph(graph), None, Some("application/x-ndjson"))
        )
      case None =>
        NotFound(GenericErrorResponse("not_found", s"no info for vault id $vaultId").asJson)
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
      val graph = searchGraph(userInfo, combinedTerm)

      Result(
        ResponseHeader(200, Map()),
        HttpEntity.Streamed(Source.fromGraph(graph), None, Some("application/x-ndjson"))
      )
    }
  }

  def testFastSearch(vaultId:String, field:String, value:String) = IsAuthenticated { uid=> request=>
    withVault(vaultId) { userInfo =>
      val graph = GraphDSL.create() { implicit builder =>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val terms = Array(
          SearchTerm.createSimpleTerm(field, value)
        )
        val src = builder.add(new OMFastSearchSource(userInfo, terms, Array("GNM_PROJECT_ID","MXFS_ACCESS_TIME","MXFS_PATH","DPSP_SIZE")))
        val outlet = src.out
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
  }
}
