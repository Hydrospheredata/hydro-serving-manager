package io.hydrosphere.serving.manager.domain.application.graph.compat

/**
  * This file contains graph definition compatible with old version of manager
  * Used in db convertation.
  */

import cats.data.NonEmptyList
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.Servable

case class Variant[T](item: T, weight: Int)

case class Node[T](variants: NonEmptyList[Variant[T]])

case class Graph[T](nodes: NonEmptyList[Node[T]])

case class ExecutionNode(variants: NonEmptyList[Variant[Servable]], signature: ModelSignature)

case class PipelineStage(
  modelVariants: NonEmptyList[Variant[ModelVersion]],
  signature: ModelSignature
)

case class VersionPipeline(stages: NonEmptyList[PipelineStage], pipelineSignature: ModelSignature)

sealed trait ExecutionGraphAdapter extends Product with Serializable

case class VersionGraphAdapter(
  stages: NonEmptyList[VersionStage]
) extends ExecutionGraphAdapter

case class VersionStage(
  modelVariants: NonEmptyList[ModelVariant],
  signature: ModelSignature
)

case class ModelVariant(
  modelVersion: ModelVersion,
  weight: Int
)

case class ServableGraphAdapter(
  stages: NonEmptyList[ServableStage],
) extends ExecutionGraphAdapter

case class ServableStage(
  modelVariants: NonEmptyList[Variant[String]],
  signature: ModelSignature
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