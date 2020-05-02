package io.hydrosphere.serving.manager.api.http.controller.host_selector

import io.circe.generic.JsonCodec

@JsonCodec
case class CreateHostSelector(
  name: String,
  nodeSelector: Map[String, String]
)