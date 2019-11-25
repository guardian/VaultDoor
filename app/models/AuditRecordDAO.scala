package models

import org.mongodb.scala.Completed

import scala.concurrent.Future

trait AuditRecordDAO {
  def insert(rec:AuditRecord):Future[Option[Completed]]
  def scan(limit:Int):Future[Seq[AuditRecord]]
}