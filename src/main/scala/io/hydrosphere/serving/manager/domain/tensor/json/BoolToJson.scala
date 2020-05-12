package io.hydrosphere.serving.manager.domain.tensor.json

import io.circe.Json
import io.circe.syntax._
import io.hydrosphere.serving.manager.domain.tensor.BoolTensor

object BoolToJson extends TensorJsonLens[BoolTensor] {
  override def convert: Boolean => Json = _.asJson
}
