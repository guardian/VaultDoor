package controllers

import akka.actor.ActorSystem
import akka.stream.{ClosedShape, Materializer, SourceShape}
import akka.stream.scaladsl.{GraphDSL, Keep, RunnableGraph, Source}
import akka.util.ByteString
import auth.{BearerTokenAuth, Security}
import com.om.mxs.client.japi.{Attribute, Constants, SearchTerm, UserInfo, Vault}
import helpers.SearchTermHelper.projectIdQuery
import helpers.{ContentSearchBuilder, UserInfoCache, ZonedDateTimeEncoder}

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.circe.Circe
import play.api.mvc.{AbstractController, ControllerComponents, EssentialAction, ResponseHeader, Result}
import responses.GenericErrorResponse
import streamcomponents.{OMFastContentSearchSource, OMFastSearchSource, OMLookupMetadata, OMSearchSource, ProjectSummarySink}
import models.{GnmMetadata, MxsMetadata, PresentableFile, ProjectSummary, ProjectSummaryEncoder, SummaryEntry}
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

      val src = builder.add(new OMSearchSource(userInfo, Some(searchTerm), None))
      val lookup = builder.add(new OMLookupMetadata(userInfo).async)

      src ~> lookup

      val outlet = lookup.out
        .log("FileListController.searchGraph")
        .map(PresentableFile.fromObjectMatrixEntry)
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
  def summaryFor(userInfo:UserInfo, query:ContentSearchBuilder) = {
    val sinkFact = new ProjectSummarySink

    ProjectSummarySink.suitableFastSource(userInfo,query).toMat(sinkFact)(Keep.right).run()
  }

  def vaultSummary(vaultId:String) = IsAuthenticatedAsync { uid=> request=>
    withVaultAsync(vaultId) { userInfo=>
      val q = ContentSearchBuilder("*")
      summaryFor(userInfo,q).map(summary=>{
        Ok(summary.asJson)
      })
    }
  }

  def projectsummary(vaultId:String, forProject:String) = IsAuthenticatedAsync { uid => request =>
    withVaultAsync(vaultId) { userInfo=>
      logger.info(s"projectsummary: looking up '$forProject' on $vaultId (${userInfo.getVault}")
      projectIdQuery(forProject) match {
        case Some(query) =>
          summaryFor(userInfo, query).map(summary => {
            Ok(summary.asJson)
          })
        case None =>
          Future(BadRequest(GenericErrorResponse("bad_request", "project id is malformed").asJson))
      }
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
      projectIdQuery(forProject) match {
        case Some(q)=>
          val graph = searchGraph(userInfo, SearchTerm.createSimpleTerm(Constants.CONTENT, q.build))
          Result(
            ResponseHeader(200, Map()),
            HttpEntity.Streamed(Source.fromGraph(graph), None, Some("application/x-ndjson"))
          )
        case None=>
          BadRequest(GenericErrorResponse("bad_request", "project id is malformed").asJson)
      }
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
        val searchAttrib = forPath match {
          case Some(searchPath)=>new Attribute(Constants.CONTENT, s"""MXFS_FILENAME:"$searchPath\nsort:>\u241DMXFS_CREATION_TIME\u241Dlong"""" )
          case None=>new Attribute(Constants.CONTENT, "*\nsort:>\u241DMXFS_CREATION_TIME\u241Dlong")
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

  def testFastSearch(vaultId:String, field:String, value:String, quoted:Boolean) = IsAuthenticated { uid=> request=>
    withVault(vaultId) { userInfo =>
      val graph = GraphDSL.create() { implicit builder =>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val searchString = ContentSearchBuilder(s"$field:$value")
          .withKeywords(GnmMetadata.Fields)
          .withKeywords(PresentableFile.MXFSFields)
          .build

        logger.debug(s"vault is $vaultId, field '$field', value '$value', quoted '$quoted'.  Search term is $searchString")
        val src = builder.add(new OMFastContentSearchSource(userInfo, searchString))
        val outlet = src.out
          .map(entry=>{
            logger.info(s"Got entry ${entry.oid} with attributes ${entry.attributes.map(_.stringValues)}")
            entry
          })
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
