package io.hydrosphere.serving.manager.util.json

import io.circe.{Decoder, Encoder}
import scalapb.{GeneratedEnum, GeneratedEnumCompanion}

object ProtosCodec {
  implicit def protoEnumDecoder[T <: GeneratedEnum](ec: GeneratedEnumCompanion[T]): Decoder[T] = {
    Decoder.decodeString.emap { str =>
      ec.fromName(str).toRight(s"Unknown proto enum value: ${str}")
    }
  }

  implicit def protoEnumEncoder[T <: GeneratedEnum](ec: GeneratedEnumCompanion[T]): Encoder[T] = {
    Encoder.encodeString.contramap(_.toString())
  }



}
