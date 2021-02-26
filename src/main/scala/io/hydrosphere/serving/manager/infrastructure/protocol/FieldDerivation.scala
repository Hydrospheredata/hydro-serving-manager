package io.hydrosphere.serving.manager.infrastructure.protocol

import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.hydrosphere.serving.manager.domain.contract.Field
import io.circe.syntax._
import cats.implicits._

object FieldDerivation {
  implicit val encF: Encoder[Field] = new Encoder[Field] {
    override def apply(a: Field): Json =
      a match {
        case x: Field.Tensor => x.asJson
        case x: Field.Map    => x.asJson
      }
  }

  implicit val decF: Decoder[Field] = new Decoder[Field] {
    override def apply(c: HCursor): Result[Field] =
      c.value.as[Field.Map] match {
        case Left(_) =>
          c.value.as[Field.Tensor].leftMap(_ => DecodingFailure("Couldn't parse Field", Nil))
        case Right(value) => Right(value)
      }
  }
}
