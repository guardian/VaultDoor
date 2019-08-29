package models

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import helpers.ObservableToPromise
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import services.MongoClientManager
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.{Completed, FindObservable, MongoCollection, Observer, Subscription, model}
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.result.DeleteResult
import org.slf4j.LoggerFactory

import scala.concurrent.Promise

@Singleton
class AuditRecordDAO @Inject() (config:Configuration, mongoClientMgr:MongoClientManager) {
  private val logger = LoggerFactory.getLogger(getClass)

  logger.debug(s"Config keys: ${config.keys}")

  private val dbName = config.get[String]("mongo.dbname")
  private val collectionName = config.get[String]("mongo.collection")
  private val codecRegistry = fromRegistries(fromProviders(classOf[AuditRecord]), DEFAULT_CODEC_REGISTRY )

  private val database = mongoClientMgr.client.getDatabase(dbName).withCodecRegistry(codecRegistry)
  private val collection:MongoCollection[AuditRecord] = database.getCollection(collectionName)

  def insert(rec:AuditRecord) = {
    val observable = collection.insertOne(rec)
    val p = Promise[Option[Completed]]()
    observable.subscribe(ObservableToPromise.make[Completed](p))

    p.future
  }

  def scan(limit:Int) = {
    val observable = collection.find().limit(limit)
    val p = Promise[Seq[AuditRecord]]
    observable.subscribe(ObservableToPromise.makeFolder(p))

    p.future
  }
}
