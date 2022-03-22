package controllers

import auth.{BearerTokenAuth, Security}
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.cache.SyncCacheApi
import play.api.libs.circe.Circe
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, ControllerComponents}

@Singleton
class Login @Inject()(override implicit val config:Configuration,
                      override val bearerTokenAuth:BearerTokenAuth,
                      cc:ControllerComponents
                     )(override implicit val cache:SyncCacheApi)
  extends AbstractController(cc) with Security with Circe {

  /**
    * Action that allows the frontend to test if the current session is valid
    * @return If the session is not valid, a 403 response
    *         If the session is valid, a 200 response with the currently logged in userid in a json object
    */
  def isLoggedIn = IsAuthenticated { uid=> { request=>
    Ok(Json.obj("status"->"ok","uid"->uid))
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
