package io.hydrosphere.serving.manager.domain.tensor.json

import io.circe.Json
import io.circe.syntax._
import io.hydrosphere.serving.manager.domain.tensor.DoubleTensor

object DoubleToJson extends TensorJsonLens[DoubleTensor] {
  override def convert: Double => Json = _.asJson
}
