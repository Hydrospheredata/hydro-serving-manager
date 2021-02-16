package io.hydrosphere.serving.manager.domain.application.requests

import cats.data.NonEmptyList
import io.circe.generic.JsonCodec

@JsonCodec
case class ExecutionGraphRequest(
  stages: NonEmptyList[PipelineStageRequest]
)