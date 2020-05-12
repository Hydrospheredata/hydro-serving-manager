package io.hydrosphere.serving.manager.domain.tensor.json

import io.circe.syntax._
import io.hydrosphere.serving.manager.domain.tensor.IntTensor

object IntToJson extends TensorJsonLens[IntTensor[_]] {
  override def convert = x => x.asInstanceOf[Int].asJson
}
