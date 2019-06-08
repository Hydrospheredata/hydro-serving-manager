package io.hydrosphere.serving.manager.domain.application.requests

import cats.data.NonEmptyList

case class ExecutionGraphRequest(
  stages: NonEmptyList[PipelineStageRequest]
)

object ExecutionGraphRequest {

  import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol._

  implicit val format = jsonFormat1(ExecutionGraphRequest.apply)
}