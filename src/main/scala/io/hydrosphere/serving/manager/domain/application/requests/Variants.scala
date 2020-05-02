package io.hydrosphere.serving.manager.domain.application.requests

import cats.data.NonEmptyList
import io.circe.generic.JsonCodec

@JsonCodec
case class ModelVariantRequest(
  modelVersionId: Long,
  weight: Int
)

@JsonCodec
case class PipelineStageRequest(
  modelVariants: NonEmptyList[ModelVariantRequest]
)

@JsonCodec
case class ExecutionGraphRequest(
  stages: NonEmptyList[PipelineStageRequest]
)