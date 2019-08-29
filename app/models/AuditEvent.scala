package models

import io.circe.{Decoder, Encoder}

object AuditEvent extends Enumeration {
  val STREAMOUT_START,STREAMOUT_END,STREAMOUT_FAILED, NOTFOUND = Value
}

trait AuditEventEncoder {
  implicit val encodeAuditEvent = Encoder.enumEncoder(AuditEvent)
  implicit val decodeAuditEvent = Decoder.enumDecoder(AuditEvent)
}