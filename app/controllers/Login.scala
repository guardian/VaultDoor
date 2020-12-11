package controllers

import auth.{BearerTokenAuth, LDAP, Security, User}
import com.unboundid.ldap.sdk.LDAPConnectionPool
import javax.inject.{Inject, Singleton}
import models.LoginRequest
import play.api.Configuration
import play.api.cache.SyncCacheApi
import play.api.libs.circe.Circe
import play.api.libs.json.{JsError, Json}
import play.api.mvc.{AbstractController, ControllerComponents}
import io.circe.generic.auto._
import io.circe.syntax._
import responses.GenericErrorResponse

import scala.collection.JavaConverters._
import scala.util.{Failure, Success}

@Singleton
class Login @Inject()(override implicit val config:Configuration,
                      override val bearerTokenAuth:BearerTokenAuth,
                      cc:ControllerComponents
                     )(override implicit val cache:SyncCacheApi)
  extends AbstractController(cc) with Security with Circe {

  private lazy val adminRoles = config.get[Option[Seq[String]]]("ldap.admin-groups").getOrElse(List("Administrator"))
  /**
    * Action to allow the client to authenticate.  Expects a JSON body containing username and password (use https!!!)
    * @return If login is successful, a 200 response containing a session cookie that authenticates the user.
    *         If unsuccessful, a 403 response
    *         If the data is malformed, a 400 response
    *         If an error occurs, a 500 response with a basic error message directing the user to go to the logs
    */
  def authenticate = Action(circe.json) { request=>
    logger.info(s"Admin roles are: $adminRoles")
    LDAP.connectionPool.fold(
      errors=> {
        logger.error("LDAP not configured properly", errors)
        InternalServerError(Json.obj("status" -> "error", "detail" -> "ldap not configured properly, see logs"))
      },
      ldapConnectionPool=> {
        implicit val pool: LDAPConnectionPool = ldapConnectionPool
        request.body.as[LoginRequest].fold(
          errors => {
            BadRequest(GenericErrorResponse("invalid_data", errors.toString).asJson)
          },
          loginRequest => {
            User.authenticate(loginRequest.username, loginRequest.password) match {
              case Success(Some(user)) =>
                Ok(Json.obj("status" -> "ok", "detail" -> "Logged in", "uid" -> user.uid, "isAdmin"->checkRole(user.uid, adminRoles))).withSession("uid" -> user.uid)
              case Success(None) =>
                logger.warn(s"Failed login from ${loginRequest.username} from host ${request.host}")
                Forbidden(Json.obj("status" -> "error", "detail" -> "forbidden"))
              case Failure(error) =>
                logger.error(s"Authentication error when trying to log in ${loginRequest.username}. This could just mean a wrong password.", error)
                Forbidden(Json.obj("status" -> "error", "detail" -> "forbidden"))
            }
          })
      }
    )
  }

  /**
    * Action that allows the frontend to test if the current session is valid
    * @return If the session is not valid, a 403 response
    *         If the session is valid, a 200 response with the currently logged in userid in a json object
    */
  def isLoggedIn = IsAuthenticated { uid=> { request=>
    val isAdmin = config.get[String]("ldap.ldapProtocol") match {
      case "none"=>true
      case _=>checkRole(uid, adminRoles)
    }

    Ok(Json.obj("status"->"ok","uid"->uid, "isAdmin"->isAdmin))
  }}

  /**
    * Action that allows the frontend to test if the user is an admin
    * @return If the user is not an admin, a 403 response. If the user is an admin, a 200 response
    */
  def checkIsAdmin = IsAdmin {uid=> {request=>
    Ok(Json.obj("status"->"ok"))
  }}

  /**
    * Action to log out, by clearing the client's session cookie.
    * @return
    */
  def logout = Action { request=>
    Ok(Json.obj("status"->"ok","detail"->"Logged out")).withNewSession
  }

}
