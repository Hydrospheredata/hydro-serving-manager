package io.hydrosphere.serving.manager.domain.tensor.json

import io.circe.Json
import io.circe.syntax._
import io.hydrosphere.serving.manager.domain.tensor.StringTensor

object StringToJson extends TensorJsonLens[StringTensor] {
  override def convert: String => Json = _.asJson
}
