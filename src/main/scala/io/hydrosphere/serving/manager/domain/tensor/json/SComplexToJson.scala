package io.hydrosphere.serving.manager.domain.tensor.json

import io.circe.Json
import io.circe.syntax._
import io.hydrosphere.serving.manager.domain.tensor.SComplexTensor

object SComplexToJson extends TensorJsonLens[SComplexTensor] {
  override def convert: Float => Json = _.asJson
}
