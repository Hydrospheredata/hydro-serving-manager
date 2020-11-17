package io.hydrosphere.serving.manager.domain.application.requests
import io.hydrosphere.serving.manager.infrastructure.protocol.CommonJsonProtocol._

case class ModelVariantRequest(
  modelVersionId: Long,
  weight: Int,
  deploymentConfigName: Option[String] = None
)

object ModelVariantRequest {
  implicit val format = jsonFormat3(ModelVariantRequest.apply)
}