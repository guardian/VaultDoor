package models

import java.time.ZonedDateTime
import java.util.UUID

import helpers.RangeHeader

case class AuditRecord (uuid:UUID, recordTime:ZonedDateTime, event:AuditEvent.Value, username:String, file:Option[AuditFile], maybeRange:Seq[RangeHeader], notes:Option[String])

object AuditRecord {
  /**
    * shortcut method to generate a new event, with randomised UUID and current time
    * @param event event type, an AuditEvent value
    * @param username username (string)
    * @param file optional AuditFile record indicating the object affected
    * @param overrideDateTime optional ZonedDateTime to override the current datetime used
    * @return a new AuditRecord instance
    */
  def apply(event:AuditEvent.Value, username:String, file:Option[AuditFile],  maybeRange:Seq[RangeHeader], notes:Option[String]=None, overrideDateTime:Option[ZonedDateTime]=None) =
    new AuditRecord(
      UUID.randomUUID(),
      overrideDateTime.getOrElse(ZonedDateTime.now()),
      event,
      username,
      file,
      maybeRange,
      notes
    )

}