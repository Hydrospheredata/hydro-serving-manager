package io.hydrosphere.serving.manager.domain.host_selector

case class HostSelector(
  id: Long,
  name: String,
  nodeSelector: Map[String, String]
)