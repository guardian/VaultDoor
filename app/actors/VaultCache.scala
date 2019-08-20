package actors

import akka.actor.Actor

object VaultCache {
  trait VCMsg

  case class RequestFileStream(applianceAddress:String, vaultId:String, objectId:String) extends VCMsg
}

class VaultCache extends Actor {
  case class ObjectEntry(vaultId:String, objectId:String)
  case class ApplianceCacheEntry(applianceAddress:String, objectEntries:Map[String,ObjectEntry])

  override def receive: Receive = {
    case _=>
  }
}