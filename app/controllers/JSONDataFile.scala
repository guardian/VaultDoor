package controllers

import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import play.api.Configuration
import play.api.http.HttpEntity
import play.api.libs.circe.Circe
import play.api.mvc.{AbstractController, ControllerComponents, ResponseHeader, Result}
import auth.{BearerTokenAuth, Security}
import play.api.cache.SyncCacheApi
import responses.GenericErrorResponse
import java.nio.file.{Files, Paths}
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import io.circe.generic.auto._
import io.circe.syntax._


class JSONDataFile @Inject() (cc:ControllerComponents, override implicit val config:Configuration, override val bearerTokenAuth:BearerTokenAuth)(implicit mat:Materializer, ec:ExecutionContext, override implicit val cache:SyncCacheApi) extends AbstractController(cc) with Security with Circe {
  def getAFile(vaultId:String)= IsAuthenticated { uid =>
    request => {
      val sanitizedVaultId = vaultId.replace("/","")
      val fullPath = Paths.get(config.get[String]("vaults.vaultDataPath"), sanitizedVaultId + ".json")
      val fileSize = Files.size(fullPath)

      if (Files.exists(fullPath)) {
        val contentType = if (fullPath.endsWith(".json")) {
          Some("application/json")
        } else {
          None
        }
        Result(
          header = ResponseHeader(200, Map.empty),
          body = HttpEntity.Streamed(FileIO.fromPath(fullPath), Some(fileSize), contentType)
        )
      } else {
        NotFound(GenericErrorResponse("error", "JSON file for vault not found.").asJson)
      }
    }
  }
}