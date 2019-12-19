package models
import org.mongodb.scala.Completed

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class AuditRecordDAONull extends AuditRecordDAO {
  override def scan(limit: Int): Future[Seq[AuditRecord]] = Future(Seq())
  override def insert(rec:AuditRecord):Future[Option[Completed]] = Future(Some(Completed()))
}
