package models

import io.circe.{Decoder, Encoder}

object AuditEvent extends Enumeration {
  val STREAMOUT, HEADFILE, NOTFOUND, OMERROR = Value
}

trait AuditEventEncoder {
  implicit val encodeAuditEvent = Encoder.enumEncoder(AuditEvent)
  implicit val decodeAuditEvent = Decoder.enumDecoder(AuditEvent)
}