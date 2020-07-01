package io.hydrosphere.serving.manager.domain.application.requests

import io.hydrosphere.serving.manager.domain.clouddriver.DeploymentConfiguration
import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol._

case class ModelVariantRequest(
  modelVersionId: Long,
  weight: Int,
  configuration: Option[DeploymentConfiguration] = None
)

object ModelVariantRequest {
  implicit val format = jsonFormat3(ModelVariantRequest.apply)
}