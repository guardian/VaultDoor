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
  private val content:Map[String,UserInfo] = loadInFiles()

  content.foreach(kvTuple=>logger.debug(s"${kvTuple._1} -> ${kvTuple._2}"))
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
  protected def loadInFiles():Map[String,UserInfo] = {
    val vaultSettingsDir = config.get[String]("vaults.settings-path")

    logger.info(s"Loading configuration files from $vaultSettingsDir")

    val dir = new File(vaultSettingsDir)

    val files = dir.listFiles(vaultFileFilter)
    if(files==null){
      logger.error(s"Could not find directory $vaultSettingsDir to load vault settings from")
      terminate(2)
    }

    val maybeUserInfos = files.map(f=>{
      if(f.isFile) {
        logger.info(s"Loading login info from ${f.getAbsolutePath}")
        Some(UserInfoBuilder.fromFile(f))
      } else {
        None
      }
    }).collect({ case Some(entry)=>entry})

    val failures = maybeUserInfos.collect({case Failure(err)=>err})

    if(failures.nonEmpty){
      logger.error(s"${failures.length} out of ${maybeUserInfos.length} vault files failed to load: ")
      failures.foreach(err=>logger.error("Vault file failed: ", err))
    }

    val userInfos = maybeUserInfos.collect({case Success(info)=>info})

    if(userInfos.isEmpty){
      logger.error(s"Could not load any vault information files from $vaultSettingsDir, exiting app")
      terminate(2)
    }

    userInfos.foreach(info=>logger.debug(s"${info.toString}: ${info.getVault} on ${info.getAddresses.mkString(",")}"))
    logger.info(s"Loaded ${userInfos.length} vault information files")

    userInfos.flatMap(i=>i.getAddresses.map(addr=>(s"$addr-${i.getVault}",i))).toMap
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
    content.map(kv=>(kv._2.getVault, kv._2.getAddresses)).toSeq
  }

  def infoForVaultId(vaultId:String) = {
    val maps = content.map(kv=>(kv._2.getVault,kv._2))
    maps.get(vaultId)
  }

}
