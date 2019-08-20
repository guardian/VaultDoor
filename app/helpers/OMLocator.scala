package helpers

import java.net.URI
import java.util.UUID

import com.om.mxs.client.japi.UserInfo

import scala.util.{Failure, Success}

object OMLocator {
  private val uriFormat = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
  val pathXtractor = s"/*($uriFormat)/($uriFormat)/(.*)".r

  def fromUri(uri:URI) = {
    if(uri.getScheme!="omms"){
      Failure(new RuntimeException("URI is not in omms scheme"))
    } else {
      uri.getPath match {
        case pathXtractor(clusterId, vaultId, path) =>
          Success(new OMLocator(uri.getHost, UUID.fromString(clusterId), UUID.fromString(vaultId), path))
        case _ =>
          Failure(new RuntimeException("Path format is incorrect. Expecting cluster-id/vault-id/filepath."))
      }
    }
  }
}

case class OMLocator(host:String,clusterId:UUID,vaultId:UUID, filePath:String)
