package io.hydrosphere.serving.manager.domain.tensor.json

import io.circe.Json
import io.circe.syntax._
import io.hydrosphere.serving.manager.domain.tensor.Uint64Tensor

object Uint64ToJson extends TensorJsonLens[Uint64Tensor] {
  override def convert: Long => Json = _.asJson
}
