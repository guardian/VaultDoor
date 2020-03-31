package models

import akka.actor.ActorSystem
import akka.stream.Materializer
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import io.circe.generic.auto._
import io.circe.syntax._
import org.slf4j.LoggerFactory

import scala.concurrent.Future

@Singleton
class ServerTokenDAORedis @Inject() (config:Configuration) (override protected implicit val actorSystem:ActorSystem, override implicit val mat:Materializer) extends ServerTokenDAO {
  private final val logger = LoggerFactory.getLogger(getClass)
  lazy val redisClient = scredis.Client(
    host = config.get[String]("redis.host"),
    port = config.getOptional[Int]("redis.port").getOrElse(6379),
    passwordOpt = config.getOptional[String]("redis.password")
  )

  override def get(tokenValue: String): Future[Option[ServerTokenEntry]] =
    redisClient.get(s"vaultdoor:servertoken:$tokenValue")
      .map(_.map(io.circe.parser.parse)).flatMap({
        case None=>Future(None)
        case Some(Left(parseError))=>
          logger.error(s"Could not parse content for server token '$tokenValue' from redis: ${parseError.toString}, deleting it")
          redisClient.del(s"vaultdoor:servertoken:$tokenValue").map(_=>None)
        case Some(Right(json))=>
          json.as[ServerTokenEntry] match {
            case Left(marshalErr)=>
              logger.error(s"Could not marshal content for server token '$tokenValue from redis: ${marshalErr.toString}, deleting it")
              redisClient.del(s"vaultdoor:servertoken:$tokenValue").map(_=>None)
            case Right(tok)=>
              Future(Some(tok))
          }
      })


  override def put(entry: ServerTokenEntry, expiresIn:Int): Future[Boolean] = {
    redisClient.setEX(s"vaultdoor:servertoken:${entry.value}",entry.asJson.noSpaces, expiresIn).map(_=>true)
  }

  override def remove(tokenValue:String):Future[Boolean] = redisClient.del(s"vaultdoor:servertoken:$tokenValue").map(_=>true)

  override def finalize(): Unit = {
    redisClient.quit()
    super.finalize()
  }
}
