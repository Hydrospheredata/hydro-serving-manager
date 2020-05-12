package io.hydrosphere.serving.manager.domain.tensor.json

import io.circe.Json
import io.circe.syntax._
import io.hydrosphere.serving.manager.domain.tensor.Int64Tensor

object Int64ToJson extends TensorJsonLens[Int64Tensor] {
  override def convert: Long => Json = _.asJson
}
