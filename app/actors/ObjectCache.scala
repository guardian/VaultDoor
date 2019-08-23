package actors

import java.util.UUID
import java.util.concurrent.TimeUnit

import actors.ObjectCache.{CacheEntry, ExpiryTick, Lookup, ObjectFound, ObjectLookupFailed, ObjectNotFound, UpdateCache}
import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.stream.Materializer
import com.om.mxs.client.japi.{MatrixStore, SearchTerm, UserInfo, Vault}
import helpers.{OMLocator, UserInfoCache}
import javax.inject.{Inject, Singleton}
import models.ObjectMatrixEntry
import org.slf4j.LoggerFactory
import play.api.Configuration

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object ObjectCache {
  trait OCMsg

  /* incoming messages */
  case class Lookup(locator:OMLocator) extends OCMsg

  /* private incoming messages */
  case class UpdateCache(locator: OMLocator, entry:ObjectMatrixEntry)
  case object ExpiryTick

  /*outgoing messages*/
  case class ObjectFound(locator:OMLocator, entry:ObjectMatrixEntry) extends OCMsg
  case class ObjectNotFound(locator: OMLocator) extends OCMsg
  case class ObjectLookupFailed(locator:OMLocator, msg:String) extends OCMsg

  /*private*/
  case class CacheEntry(entry:ObjectMatrixEntry, lastUsed:Long) {
    def updated():CacheEntry = this.copy(lastUsed=java.time.Instant.now().getEpochSecond)
  }
}

/**
  * Caching lookup actor for ObjectMatrix files.
  * Send an Assk with the Lookup message passing an [[OMLocator]] instance and you will either get [[ObjectFound]]  with the OID
  * or [[ObjectNotFound]] or [[ObjectLookupFailed]] in reply
  * Initiate this via guice dependency injection.
  * @param userInfoCache
  * @param config
  * @param system
  */
@Singleton
class ObjectCache @Inject() (userInfoCache:UserInfoCache, config:Configuration)(implicit mat:Materializer, system:ActorSystem) extends Actor {
  private val logger = LoggerFactory.getLogger(getClass)
  //map of (vaultid,path)->oid
  protected var content:Map[(UUID,String),CacheEntry] = Map()

  protected val ownRef:ActorRef = self
  private val expiryTime:Duration = config.getOptional[String]("vaults.lookup-expiry-time-seconds").map(Duration.apply).getOrElse(Duration(1, TimeUnit.MINUTES))

  protected def setupTimer() = system.scheduler.schedule(1.minute, 1.minute,ownRef,ExpiryTick)

  setupTimer()
  /**
    * locate files for the given filename, as stored in the metadata. This assumes that one or at most two records will
    * be returned and should therefore be more efficient than using the streaming interface. If many records are expected,
    * this will be inefficient and you should use the streaming interface.
    * this will return a Future to avoid blocking any other lookup requests that would hit the cache
    * @param fileName file name to search for
    * @return a Future, containing either a sequence of zero or more results as String oids or an error
    */
  def findByFilename(userInfo:UserInfo, fileName:String):Future[Option[ObjectMatrixEntry]] = Future {
    implicit val vault = MatrixStore.openVault(userInfo)
    try {
      logger.debug(s"Lookup $fileName on ${vault.getId}")
      val searchTerm = SearchTerm.createSimpleTerm("MXFS_FILENAME", fileName) //FIXME: check the metadata field namee
      val iterator = vault.searchObjectsIterator(searchTerm, 1).asScala

      var finalSeq: Seq[String] = Seq()
      while (iterator.hasNext) { //the iterator contains the OID
        finalSeq ++= Seq(iterator.next())
      }
      if(finalSeq.length>1) logger.warn(s"Found ${finalSeq.length} object matching $fileName, only using the first")

      finalSeq.headOption match {
        case Some(oid)=>ObjectMatrixEntry(oid).getMetadata.map(entry=>Some(entry))
        case None=>Future(None)
      }
    } finally {
      vault.dispose()
    }
  }.flatten

  override def receive: Receive = {
    case UpdateCache(locator, oid)=>
      logger.debug(s"Updating cache with $oid for $locator")
      content = content ++ Map((locator.vaultId, locator.filePath)->CacheEntry(oid, java.time.Instant.now().getEpochSecond))
      logger.debug(content.toString())

    case ExpiryTick=> //purge out stale cache values
      logger.debug("expiry tick")
      val expiryThreshold = java.time.Instant.now().getEpochSecond - expiryTime.toSeconds
      content = content.filter(kv=>kv._2.lastUsed<expiryThreshold)

    case Lookup(locator)=>
      logger.debug(locator.toString)
      content.get((locator.vaultId, locator.filePath)) match {
        case Some(entry)=>
          logger.info(s"Cache hit for ${locator.filePath}")
          content = content ++ Map((locator.vaultId, locator.filePath)->entry.updated())
          sender ! ObjectFound(locator, entry.entry)
        case None=>
          logger.info(s"Cache miss for ${locator.filePath}")
          val originalSender = sender()

          userInfoCache.infoForAddress(locator.host, locator.vaultId.toString) match {
            case None=>
              logger.info(s"No login information for vault ${locator.vaultId} on ${locator.host}")
              originalSender ! ObjectNotFound(locator)
            case Some(userInfo)=>
              findByFilename(userInfo, locator.filePath).onComplete({
                case Failure(err)=>
                  logger.error(s"Could not look up $locator on OM appliance: ", err)
                  originalSender ! ObjectLookupFailed(locator, "Appliance lookup failed")
                case Success(maybeEntry)=>
                  if(maybeEntry.isEmpty){
                    originalSender ! ObjectNotFound(locator)
                  } else {
                    ownRef ! UpdateCache(locator, maybeEntry.get)
                    originalSender ! ObjectFound(locator, maybeEntry.get)
                  }
              })
          }
      }
  }
}
