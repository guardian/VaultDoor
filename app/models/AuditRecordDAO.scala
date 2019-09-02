package models

import ai.snips.bsonmacros.{BaseDAO, CodecGen, DatabaseContext}
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import helpers.{ObservableToPromise, RangeHeader, ZonedDateTimeCodec}
import javax.inject.{Inject, Singleton}
import org.bson.codecs.configuration.CodecRegistries
import play.api.Configuration
import services.MongoClientManager
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.{Completed, FindObservable, MongoCollection, Observer, Subscription, model}
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.result.DeleteResult
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Promise}

@Singleton
class AuditRecordDAO @Inject() (config:Configuration,dbContext:DatabaseContext)(implicit ec:ExecutionContext) extends BaseDAO[AuditRecord]{
  private val logger = LoggerFactory.getLogger(getClass)

  private val dbName = config.get[String]("mongodb.dbname")
  private val collectionName = config.get[String]("mongodb.collection")
  //private val codecRegistry = fromRegistries(CodecRegistries.fromCodecs(new ZonedDateTimeCodec, new AuditEventCodec),fromProviders(classOf[AuditRecord]), DEFAULT_CODEC_REGISTRY )
  dbContext.codecRegistry.register(new ZonedDateTimeCodec)


  //private val database = mongoClientMgr.client.getDatabase(dbName).withCodecRegistry(codecRegistry)\
  private val db = dbContext.database(dbName)
  CodecGen[RangeHeader](dbContext.codecRegistry)
  CodecGen[AuditFile](dbContext.codecRegistry)
  CodecGen[AuditRecord](dbContext.codecRegistry)
  override val collection:MongoCollection[AuditRecord] = db.getCollection(collectionName)

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
