package io.hydrosphere.serving.manager.domain.host_selector

import io.circe.generic.JsonCodec

@JsonCodec
case class HostSelector(
  id: Long,
  name: String,
  nodeSelector: Map[String, String]
)