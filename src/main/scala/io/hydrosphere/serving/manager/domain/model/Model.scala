package io.hydrosphere.serving.manager.domain.model

import io.circe.generic.JsonCodec

@JsonCodec
case class Model(
    id: Long,
    name: String
) {}
