package io.hydrosphere.serving.manager.domain.application.requests

import cats.data.NonEmptyList

case class PipelineStageRequest(
  modelVariants: NonEmptyList[ModelVariantRequest]
)