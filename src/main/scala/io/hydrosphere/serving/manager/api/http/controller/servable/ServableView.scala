package io.hydrosphere.serving.manager.api.http.controller.servable

import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.domain.servable.Servable

@JsonCodec
case class ServableView(
    modelVersion: Long,
    status: Servable.Status,
    fullName: String,
    metadata: Map[String, String],
    deploymentConfiguration: String,
    statusMessage: Option[String]
)

object ServableView {
  def fromServable(s: Servable): ServableView =
    ServableView(
      modelVersion = s.modelVersion.id,
      status = s.status,
      statusMessage = s.message,
      fullName = s.name,
      metadata = s.metadata,
      deploymentConfiguration = s.deploymentConfiguration.name
    )
}
