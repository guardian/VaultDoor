package models

import io.circe.{Decoder, Encoder}

object AuditEvent extends Enumeration {
  type AuditEvent = Value
  val STREAMOUT, STREAMOUT_START, STREAMOUT_END, STREAMOUT_SHORT, HEADFILE, NOTFOUND, OMERROR = Value
}

trait AuditEventEncoder {
  implicit val encodeAuditEvent = Encoder.enumEncoder(AuditEvent)
  implicit val decodeAuditEvent = Decoder.enumDecoder(AuditEvent)
}