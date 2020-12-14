package helpers

import java.io.{File, FilenameFilter}

import akka.actor.{ActorSystem, CoordinatedShutdown}
import com.om.mxs.client.japi.UserInfo
import javax.inject.{Inject, Singleton}
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.inject.ApplicationLifecycle

import scala.concurrent.Await
import scala.util.{Failure, Success}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton()
class UserInfoCache @Inject() (config:Configuration,system:ActorSystem){
  private val logger = LoggerFactory.getLogger(getClass)
  private val content:Map[String,UserInfoBuilder] = loadInFiles()
  final val byVaultId:Map[String, Seq[UserInfoBuilder]] = mapForVaultId

  private val vaultFileFilter = new FilenameFilter {
    override def accept(dir: File, name: String): Boolean ={
      logger.warn(s"checking $name: ${name.endsWith(".vault")}")
      name.endsWith(".vault")
    }

  }

  /**
    * shuts down the app in the case of a fatal error. Does not return.
    * @param exitCode exit code to return
    */
  private def terminate(exitCode:Int) = {
    Await.ready(system.terminate().andThen({
      case _=>System.exit(exitCode)
    }), 2 hours)
  }

  /**
    * loads .vault information/login files from the specified directory and returns a map of
    * (ip address, vaultid)=>(userinfo). Userinfo entries with multiple addresses get multiple entries in the
    * returned map, one for each address each pointing to the same `UserInfo` instance.
    * @return Map of (String,UserInfo)
    */
  protected def loadInFiles():Map[String,UserInfoBuilder] = {
    val vaultSettingsDir = config.get[String]("vaults.settings-path")

    logger.info(s"Loading configuration files from $vaultSettingsDir")

    val dir = new File(vaultSettingsDir)

    val files = dir.listFiles(vaultFileFilter)
    if(files==null){
      logger.error(s"Could not find directory $vaultSettingsDir to load vault settings from")
      terminate(2)
    }

    val maybeBuilders = files.map(f=>{
      if(f.isFile) {
        logger.info(s"Loading login info from ${f.getAbsolutePath}")
        Some(UserInfoBuilder.builderFromFile(f))
      } else {
        None
      }
    }).collect({ case Some(entry)=>entry})

    val failures = maybeBuilders.collect({case Failure(err)=>err})

    if(failures.nonEmpty){
      logger.error(s"${failures.length} out of ${maybeBuilders.length} vault files failed to load: ")
      failures.foreach(err=>logger.error("Vault file failed: ", err))
    }

    val builders = maybeBuilders.collect({case Success(info)=>info})

    if(builders.isEmpty){
      logger.error(s"Could not load any vault information files from $vaultSettingsDir, exiting app")
      terminate(2)
    }

    //userInfos.foreach(info=>logger.debug(s"${info.toString}: ${info.getVault} on ${info.getAddresses.mkString(",")}"))
    val names = builders.map(_.vaultName.getOrElse("(no description)"))
    logger.info(s"Loaded ${builders.length} vault information files: $names")
    
    builders.map(b=>{
      b.getUserInfo.map(userInfo=>{
        userInfo.getAddresses.map(addr=>(s"$addr-${userInfo.getVault}", b))
      })
    })
      .collect({case Success(elem)=>elem})
      .flatten
      .toMap
  }

  /**
    * look up vault login information for the given appliance and vault ID
    * @param address IP address for appliance
    * @param vaultId vault ID as a string
    * @return either a UserInfo object in an Option or None if no such obect could be found
    */
  def infoForAddress(address:String, vaultId:String) = {
    logger.debug(content.toString)
    logger.debug(s"looking up ${(address, vaultId)}")
    content.get(s"$address-$vaultId")
  }

  def allKnownVaults() = {
    content.values
  }

  /**
    * makes a shortcut map for lookups by vault ID
    */
  protected def mapForVaultId = {
    def addToMap(entry:(String, UserInfoBuilder), remaining:Seq[(String, UserInfoBuilder)], existingEntries:Map[String, Seq[UserInfoBuilder]]): Map[String, Seq[UserInfoBuilder]] = {
      val updated = existingEntries.get(entry._1) match {
        case Some(existingValues)=>
          val newValues = existingValues :+ entry._2
          existingEntries + (entry._1 -> newValues)
        case None=>
          val newValues = Seq(entry._2)
          existingEntries + (entry._1 -> newValues)
      }
      if(remaining.isEmpty) {
        updated
      } else {
        addToMap(remaining.head, remaining.tail, updated)
      }
    }

    val contentSeq = content.toSeq

    contentSeq.headOption match {
      case Some(firstEntry)=>
        addToMap (firstEntry, contentSeq.tail, Map () )
      case None=>
        logger.warn("No vaults were available to map by ID")
        Map[String, Seq[UserInfoBuilder]]()
    }
  }

  /**
    * look up the UserInfo for a given vault ID
    * @param vaultId vault ID to look up
    * @return
    */
  def infoForVaultId(vaultId:String) = {
    byVaultId
      .get(vaultId)
      .flatMap(_.headOption)
      .map(_.getUserInfo) match {
      case Some(Success(userInfo))=>Some(userInfo)
      case Some(Failure(err))=>
        logger.error(s"Could not instatiate UserInfor for vault $vaultId: ${err.getMessage}", err)
        None
      case None=>None
    }
  }

}
