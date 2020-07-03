package io.hydrosphere.serving.manager.domain.application.graph

import cats.data.NonEmptyList
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.domain.application.graph.VersionGraphComposer.PipelineStage
import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfiguration
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion


sealed trait ExecutionGraphAdapter extends Product with Serializable

case class VersionGraphAdapter(
  stages: NonEmptyList[VersionStage]
) extends ExecutionGraphAdapter
case class VersionStage(
  modelVariants: NonEmptyList[DeploymentModelVariant],
  signature: ModelSignature
)
case class DeploymentModelVariant(
  modelVersion: ModelVersion.Internal,
  weight: Int,
  deploymentConfig: Option[DeploymentConfiguration] = None
)

case class ServableGraphAdapter(
  stages: NonEmptyList[ServableStage],
) extends ExecutionGraphAdapter
case class ServableVariant(
  item: String,
  weight: Int,
  deploymentConfig: Option[DeploymentConfiguration] = None,
)
case class ServableStage(
  modelVariants: NonEmptyList[ServableVariant],
  signature: ModelSignature
)

object ExecutionGraphAdapter {
  def fromVersionPipeline(pipeline: NonEmptyList[PipelineStage]) = {
    VersionGraphAdapter(
      pipeline.map { s =>
        VersionStage(
          modelVariants = s.modelVariants.map(v => DeploymentModelVariant(v.item, v.weight)),
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