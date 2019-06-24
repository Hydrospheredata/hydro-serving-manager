package io.hydrosphere.serving.manager.api.http.controller.host_selector

case class CreateHostSelector(
  name: String,
  nodeSelector: Map[String, String]
)
