package models

import helpers.{ESClientManager, ZonedDateTimeEncoder}
import io.circe.generic.auto._
import io.circe.syntax._
import javax.inject.{Inject, Singleton}
import org.mongodb.scala.Completed
import org.slf4j.LoggerFactory
import play.api.Configuration

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class AuditRecordDAOElastic @Inject()(esClientManager:ESClientManager, config:Configuration) extends AuditRecordDAO with ZonedDateTimeEncoder {
  import com.sksamuel.elastic4s.http.ElasticDsl._
  import com.sksamuel.elastic4s.circe._
  private val logger = LoggerFactory.getLogger(getClass)

  private val esClient = esClientManager.getClient()
  private val indexName = config.getOptional[String]("elasticsearch.auditLogIndex").getOrElse("vaultdoor-audit")

  override def insert(rec: AuditRecord): Future[Option[Completed]] = esClient.execute {
    indexInto(indexName) doc(rec)
  }.map(result=>{
    if(result.isError){
      throw new RuntimeException(result.error.reason) //fail the Future
    } else {
      Some(Completed())
    }
  })

  override def scan(limit: Int): Future[Seq[AuditRecord]] = esClient.execute {
    search(indexName) query matchAllQuery() limit(limit)
  }.map(response=>{
    if(response.isError){
      throw new RuntimeException(response.error.reason)
    } else {
      response.result.to[AuditRecord]
    }
  })

}
