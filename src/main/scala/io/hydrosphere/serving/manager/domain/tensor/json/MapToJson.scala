package io.hydrosphere.serving.manager.domain.tensor.json

import io.circe.syntax._
import io.hydrosphere.serving.manager.domain.tensor.MapTensor

object MapToJson extends TensorJsonLens[MapTensor] {
  override def convert = { dict =>
    val fields = dict.mapValues(TensorJsonLens.toJson).toMap
    fields.asJson
  }
}
