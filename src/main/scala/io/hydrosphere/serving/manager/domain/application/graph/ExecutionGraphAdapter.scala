package io.hydrosphere.serving.manager.domain.application.graph

import cats.data.NonEmptyList
import io.circe.generic.JsonCodec
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.domain.application.graph.VersionGraphComposer.PipelineStage
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion

@JsonCodec
sealed trait ExecutionGraphAdapter extends Product with Serializable

@JsonCodec
case class VersionGraphAdapter(
    stages: NonEmptyList[VersionStage]
) extends ExecutionGraphAdapter

@JsonCodec
case class VersionStage(
    modelVariants: NonEmptyList[ModelVariant],
    signature: Signature
)
@JsonCodec
case class ModelVariant(
    modelVersion: ModelVersion.Internal,
    weight: Int
)

@JsonCodec
case class ServableGraphAdapter(
    stages: NonEmptyList[ServableStage]
) extends ExecutionGraphAdapter

@JsonCodec
case class ServableStage(
    modelVariants: NonEmptyList[Variant[String]],
    signature: Signature
)

object ExecutionGraphAdapter {
  def fromVersionPipeline(pipeline: NonEmptyList[PipelineStage]) = {
    VersionGraphAdapter(
      pipeline.map { s =>
        VersionStage(
          modelVariants = s.modelVariants.map(v => ModelVariant(v.item, v.weight)),
          signature = s.signature
        )
      }
    )
  }

  def fromServablePipeline(pipeline: NonEmptyList[ExecutionNode]) = {
    ServableGraphAdapter(
      pipeline.map { s =>
        ServableStage(
          modelVariants = s.variants.map(x => x.copy(item = x.item.fullName)),
          signature = s.signature
        )
      }
    )
  }
}
