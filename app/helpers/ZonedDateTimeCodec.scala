package helpers

import java.time.{Instant, ZoneId, ZonedDateTime}

import org.bson.{BsonReader, BsonWriter}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}

class ZonedDateTimeCodec extends Codec[ZonedDateTime]{
  override def encode(writer: BsonWriter, value: ZonedDateTime, encoderContext: EncoderContext): Unit =
    writer.writeInt64(value.toInstant.toEpochMilli)

  override def decode(reader: BsonReader, decoderContext: DecoderContext): ZonedDateTime =
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(reader.readInt64()),ZoneId.systemDefault())

  override def getEncoderClass: Class[ZonedDateTime] = classOf[ZonedDateTime]
}
