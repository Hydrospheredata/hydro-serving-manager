package io.hydrosphere.serving.manager.domain.application.requests

import cats.data.NonEmptyList

case class PipelineStageRequest(
  modelVariants: NonEmptyList[ModelVariantRequest]
)

object PipelineStageRequest {
  import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol._
  implicit val format = jsonFormat1(PipelineStageRequest.apply)
}