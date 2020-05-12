package io.hydrosphere.serving.manager.domain.application.compat

import cats.data.NonEmptyList
import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion

@JsonCodec
case class PipelineStage(
    modelVariants: NonEmptyList[Variant[ModelVersion.Internal]],
    signature: Signature
)
