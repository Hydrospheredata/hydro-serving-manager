package io.hydrosphere.serving.manager.domain.application.requests
import io.circe.generic.JsonCodec

@JsonCodec
case class ModelVariantRequest(
  modelVersionId: Long,
  weight: Int,
  deploymentConfigName: Option[String] = None
)
