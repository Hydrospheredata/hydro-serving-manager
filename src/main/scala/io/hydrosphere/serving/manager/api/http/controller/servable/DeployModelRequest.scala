package io.hydrosphere.serving.manager.api.http.controller.servable

import io.hydrosphere.serving.manager.domain.clouddriver.DeploymentConfiguration

final case class DeployModelRequest(
  modelName: String,
  version: Long,
  metadata: Option[Map[String, String]],
  configuration: Option[DeploymentConfiguration]
)