package models

import ai.snips.bsonmacros.{BaseDAO, CodecGen, DatabaseContext}
import helpers.{ObservableToPromise, RangeHeader, ZonedDateTimeCodec}
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import org.mongodb.scala.{Completed, MongoCollection}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Promise}

@Singleton
class AuditRecordDAO @Inject() (config:Configuration,dbContext:DatabaseContext)(implicit ec:ExecutionContext) extends BaseDAO[AuditRecord]{
  private val logger = LoggerFactory.getLogger(getClass)

  private val dbName = config.get[String]("mongodb.dbname")
  private val collectionName = config.get[String]("mongodb.collection")
  dbContext.codecRegistry.register(new ZonedDateTimeCodec)

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
