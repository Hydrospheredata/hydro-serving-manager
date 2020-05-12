package io.hydrosphere.serving.manager.domain.tensor.json

import io.circe.Json
import io.circe.syntax._
import io.hydrosphere.serving.manager.domain.tensor.FloatTensor

object FloatToJson extends TensorJsonLens[FloatTensor] {
  override def convert: Float => Json = _.asJson
}
