package helpers
import java.nio.ByteBuffer

import actors.ObjectCache.{Lookup, OCMsg, ObjectFound, ObjectLookupFailed, ObjectNotFound}
import akka.actor.ActorRef
import com.om.mxs.client.japi.{AccessOption, MatrixStore, SeekableByteChannel, UserInfo}
import javax.inject.{Inject, Named, Singleton}
import akka.pattern.ask
import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._

/**
  * very basic initial OM access class
  */
@Singleton
class OMAccess @Inject() (userInfoCache:UserInfoCache, @Named("object-cache")objectCache:ActorRef){
  def getChannel(locator:OMLocator, oid:String, readChunkSize:Int) = {
    userInfoCache.infoForAddress(locator.host, locator.vaultId.toString) match {
      case Some(userinfo)=>
        val maybeVault = Try {
          MatrixStore.openVault(userinfo)
        }

        maybeVault.flatMap(v => Try {
            v.getObject(oid)
          }).map(mxsObject => mxsObject.newSeekableObjectChannel(Set(AccessOption.READ).asJava, readChunkSize))
      case None=>
        Failure(new RuntimeException(s"No login information for vault ${locator.vaultId} on ${locator.host}"))
      }
  }

  def loadChunk(channel:SeekableByteChannel, startAtOffset:Long, finishAtOffset:Long):Try[ByteBuffer] = Try {
    val chnlToReaad = channel.position(startAtOffset)

    val buffer = ByteBuffer.allocate((finishAtOffset-startAtOffset).toInt)
    val bytesRead = chnlToReaad.read(buffer)  //reads in bytes to the remaining capacity of the buffer

    buffer
  }

  def loadAll(channel: SeekableByteChannel): Try[ByteBuffer] = Try {
    val buffer = ByteBuffer.allocate(channel.size().toInt)
    val bytesRead = channel.read(buffer)
    buffer
  }
}