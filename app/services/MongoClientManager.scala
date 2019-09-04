package services

import javax.inject.{Inject, Singleton}
import org.mongodb.scala.MongoClient
import org.slf4j.LoggerFactory
import play.api.Configuration

@Singleton
class MongoClientManager @Inject()(config:Configuration){
  private val logger = LoggerFactory.getLogger(getClass)

  val client = {
    val serverUri = config.get[String]("mongodb.uri") //.getOrElse("mongodb://localhost:27017")
    logger.info(s"Setting up new Mongo client to $serverUri")
    MongoClient(serverUri)
  }
}
