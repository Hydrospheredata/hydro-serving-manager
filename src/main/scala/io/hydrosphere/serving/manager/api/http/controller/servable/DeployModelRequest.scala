package io.hydrosphere.serving.manager.api.http.controller.servable

final case class DeployModelRequest(
  modelName: String,
  version: Long,
  metadata: Option[Map[String, String]],
  deploymentConfigName: Option[String]
)