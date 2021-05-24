package io.hydrosphere.serving.manager.domain.servable

import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfiguration
import io.hydrosphere.serving.manager.domain.servable.Servable.Status

@JsonCodec
case class ServableView(
    modelVersionId: Long,
    name: String,
    status: Status,
    message: Option[String],
    host: Option[String],
    port: Option[Int],
    usedApps: List[String] = Nil,
    metadata: Map[String, String] = Map.empty,
    deploymentConfiguration: DeploymentConfiguration
)

object ServableView {
  def fromServable(servable: Servable): ServableView =
    ServableView(
      modelVersionId = servable.modelVersion.id,
      name = servable.name,
      status = servable.status,
      message = servable.message,
      host = servable.host,
      port = servable.port,
      usedApps = servable.usedApps,
      metadata = servable.metadata,
      deploymentConfiguration = servable.deploymentConfiguration
    )
}
