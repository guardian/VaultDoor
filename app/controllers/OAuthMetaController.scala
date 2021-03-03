package controllers

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.circe.Circe
import play.api.mvc.{AbstractController, ControllerComponents}
import responses.{GenericErrorResponse, OAuthConfigResponse}
import io.circe.syntax._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

import scala.io.Source

@Singleton
class OAuthMetaController @Inject() (config:Configuration, cc:ControllerComponents) extends AbstractController(cc) with Circe {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * present the JWT signing key to the frontend so it can validate JWTs
   * @return
   */
  def signingKey = Action {
    config.getOptional[String]("auth.tokenSigningCertPath") match {
      case None=>
        InternalServerError(GenericErrorResponse("config_error","No signing cert configured on server").asJson)
      case Some(certPath)=>
        try {
          val src = Source.fromFile(certPath, "UTF-8")
          val content = src.mkString
          src.close()

          Ok(content).as("application/x-x509-ca-cert")
        } catch {
          case err:Throwable=>
            logger.error(s"Could not read signing cert at $certPath: ", err)
            InternalServerError(GenericErrorResponse("error","Could not read signing cert, see server logs").asJson)
        }
    }
  }

  def oauthConfig = Action {
    try {
      val response = OAuthConfigResponse(
        config.get[String]("auth.clientId"),
        config.get[String]("auth.resource"),
        config.get[String]("auth.oAuthUri"),
        config.get[String]("auth.tokenUri"),
        config.get[Seq[String]]("auth.validAudiences"),
        config.get[String]("auth.adminClaimName"),
      )

      Ok(response.asJson)
    } catch {
      case err:Throwable=>
        logger.error("Could not get oauth2 configuration: ", err)
        InternalServerError(GenericErrorResponse("config_error","Oauth2 is not configurated correctly, consult the server logs").asJson)
    }
  }
}
