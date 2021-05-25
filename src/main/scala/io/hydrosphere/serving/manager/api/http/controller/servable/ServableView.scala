package io.hydrosphere.serving.manager.api.http.controller.servable

import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.domain.servable.Servable

@JsonCodec
case class ServableView(
    modelVersionId: Long,
    status: Servable.Status,
    statusMessage: Option[String],
    fullName: String,
    metadata: Map[String, String],
    deploymentConfigurationName: String
)

object ServableView {
  def fromServable(s: Servable): ServableView =
    ServableView(
      s.modelVersion.id,
      s.status,
      s.message,
      s.name,
      s.metadata,
      s.deploymentConfiguration.name
    )
}
