package io.hydrosphere.serving.manager.domain.application.compat

import cats.data.NonEmptyList
import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.domain.contract.Signature

@JsonCodec
case class VersionPipeline(stages: NonEmptyList[PipelineStage], pipelineSignature: Signature)
