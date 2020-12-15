package helpers

import com.om.mxs.client.japi.UserInfo
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.Configuration
import testhelpers.AkkaTestkitSpecs2Support

import scala.util.Success

class UserInfoCacheSpec extends Specification with Mockito {
  def makeMockUserInfoBuilder(server:String, vaultID:String) = {
    val m = mock[UserInfoBuilder]
    m.addresses returns Some(server)
    m.vault returns Some(vaultID)
    val info = mock[UserInfo]
    info.getAddresses returns Array(server)
    info.getVault returns vaultID
    m.getUserInfo returns Success(info)
    m
  }
  "UserInfoCache.infoForVaultId" should {
    "return the first available vault info for the given vault id" in new AkkaTestkitSpecs2Support {
      val mockVaultData:Map[String, UserInfoBuilder] = Map(
        "server1-vault1" -> makeMockUserInfoBuilder("server1","vault1"),
        "server1-vault2" -> makeMockUserInfoBuilder("server1","vault2"),
        "server2-vault1" -> makeMockUserInfoBuilder("server2","vault1"),
        "server2-vault2" -> makeMockUserInfoBuilder("server2","vault1")
      )

      val toTest = new UserInfoCache(Configuration.empty, system) {
        override protected def loadInFiles(): Map[String, UserInfoBuilder] = mockVaultData
      }

      val result = toTest.infoForVaultId("vault1")
      result must beSome
      result.get.getAddresses mustEqual Array("server1")
      result.get.getVault mustEqual "vault1"
    }
  }
}
