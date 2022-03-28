import org.slf4j.LoggerFactory
import services.DuplicateFinderService
import akka.actor.ActorSystem
import akka.stream.Materializer
import helpers.UserInfoCache
import play.api.Configuration
import com.typesafe.config.ConfigFactory
import io.circe.syntax._
import io.circe.generic.auto._
import scala.concurrent.ExecutionContext.Implicits.global
import java.nio.file.{Paths, Files}
import java.nio.charset.StandardCharsets
import scala.concurrent.Await

object Main {
  val logger = LoggerFactory.getLogger(getClass)
  implicit lazy val actorSystem:ActorSystem = ActorSystem("duplicatefinder")
  implicit lazy val mat:Materializer = Materializer.matFromSystem(actorSystem)

  def terminate(exitCode:Int) = {
    import scala.concurrent.duration._
    try {
      Await.ready(actorSystem.terminate().andThen({
        case _ => sys.exit(exitCode)
      }), 1.hour)
    } catch {
      case _:Throwable=>
        logger.warn("Could not shut down Actor System after one hour.")
        sys.exit(255)
    }
  }

  def main(args: Array[String]): Unit = {
    val vaultId = sys.env.get("VAULT_ID") match {
      case None=>
        logger.error("You must specify VAULT_ID in the environment.")
        sys.exit(1)
      case Some(vaultI)=>
        vaultI
    }

    val vaultDataOutputPath = sys.env.get("VAULT_DATA_OUTPUT_PATH") match {
      case None=>
        logger.error("You must specify VAULT_DATA_OUTPUT_PATH in the environment.")
        sys.exit(1)
      case Some(vaultPath)=>
        vaultPath
    }

    val conf = ConfigFactory.load()
    val configObject = new Configuration(conf)
    val userCache = new UserInfoCache(configObject, actorSystem)
    val duplicateFinder = new DuplicateFinderService(userCache)

    duplicateFinder.getDuplicateData(vaultId).map(duplicateData=>{
      val stringContent = duplicateData.asJson.toString()
      val filePathString = vaultDataOutputPath + "/" + vaultId + ".json"
      logger.info(s"About to write JSON file to $filePathString.")
      Files.write(Paths.get(filePathString), stringContent.getBytes(StandardCharsets.UTF_8))
      terminate(0)
    }
    ).recover({
      case _=>sys.exit(1)
    })
  }
}

