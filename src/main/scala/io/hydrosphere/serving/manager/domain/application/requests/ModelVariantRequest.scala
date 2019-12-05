package io.hydrosphere.serving.manager.domain.application.requests

import io.hydrosphere.serving.manager.infrastructure.codec.CompleteJsonProtocol._

case class ModelVariantRequest(
  modelVersionId: Long,
  weight: Int
)