package io.hydrosphere.serving.manager.domain.model

import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.grpc.entities.{Model => GModel}

@JsonCodec
case class Model(
  id: Long,
  name: String
) {
  def toGrpc = GModel(
    id = id,
    name = name
  )
}
