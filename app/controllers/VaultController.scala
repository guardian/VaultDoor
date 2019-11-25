package controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import auth.Security
import helpers.{OMAccess, UserInfoCache, ZonedDateTimeEncoder}
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.cache.SyncCacheApi
import play.api.libs.circe.Circe
import play.api.mvc.{AbstractController, ControllerComponents}
import responses.KnownVaultResponse
import io.circe.generic.auto._
import io.circe.syntax._

@Singleton
class VaultController @Inject() (cc:ControllerComponents,
                                  config:Configuration,
                                  userInfoCache: UserInfoCache
                                )(override implicit val cache:SyncCacheApi)
  extends AbstractController(cc) with Security with ObjectMatrixEntryMixin with Circe with ZonedDateTimeEncoder{

  def knownVaults() = Action {
    val content = userInfoCache.allKnownVaults()
    val responses = content.map(entry=>
      (KnownVaultResponse.apply _) tupled entry
    )

    Ok(responses.asJson)
  }
}
