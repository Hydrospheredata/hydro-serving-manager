package io.hydrosphere.serving.manager.domain.tensor.json

import io.circe.Json
import io.circe.syntax._
import io.hydrosphere.serving.manager.domain.tensor.DComplexTensor

object DComplexToJson extends TensorJsonLens[DComplexTensor] {
  override def convert: Double => Json = _.asJson
}
