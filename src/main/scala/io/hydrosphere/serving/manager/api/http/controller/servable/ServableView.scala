package io.hydrosphere.serving.manager.api.http.controller.servable

import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfiguration
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.Servable

@JsonCodec
case class ServableView(
    modelVersion: ModelVersion.Internal,
    status: Servable.Status,
    fullName: String,
    metadata: Map[String, String],
    deploymentConfiguration: DeploymentConfiguration
)

object ServableView {
  def fromServable(s: Servable) =
    ServableView(
      modelVersion = s.modelVersion,
      status = s.status,
      fullName = s.fullName,
      metadata = s.metadata,
      deploymentConfiguration = s.deploymentConfiguration
    )
}
