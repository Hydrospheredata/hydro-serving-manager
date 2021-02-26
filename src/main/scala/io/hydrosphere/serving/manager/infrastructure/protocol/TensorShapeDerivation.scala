package io.hydrosphere.serving.manager.infrastructure.protocol

import io.circe.{Decoder, Encoder, HCursor, Json}
import io.hydrosphere.serving.manager.domain.contract.TensorShape
import io.hydrosphere.serving.manager.domain.contract.TensorShape.{Dynamic, Static}
import io.circe.syntax._

object TensorShapeDerivation {
  implicit val enc: Encoder[TensorShape] = (a: TensorShape) =>
    a match {
      case v: Static => v.asJson
      case Dynamic   => Json.Null
    }

  implicit val dec: Decoder[TensorShape] = (c: HCursor) => {
    if (c.value.isNull)
      Right(Dynamic)
    else
      c.downField("dims")
        .as[List[Long]]
        .map(Static.apply)
  }
}
