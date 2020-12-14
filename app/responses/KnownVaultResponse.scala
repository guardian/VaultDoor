package responses

import helpers.UserInfoBuilder
import org.slf4j.LoggerFactory

case class KnownVaultResponse (vaultId:String, name:String)

object KnownVaultResponse {
  private val logger = LoggerFactory.getLogger(getClass)

  def fromBuilder(b:UserInfoBuilder) = {
    (b.vault, b.vaultName) match {
      case (Some(vaultId), Some(vaultName))=>
        Some(KnownVaultResponse(vaultId, vaultName))
      case _=>
        logger.warn(s"UserInfoBuilder $b was missing some data for frontend")
        None
    }
  }
}