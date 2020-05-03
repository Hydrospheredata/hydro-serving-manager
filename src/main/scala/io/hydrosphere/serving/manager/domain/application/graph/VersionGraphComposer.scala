package io.hydrosphere.serving.manager.domain.application.graph

import cats.data.NonEmptyList
import cats.implicits._
import io.circe.generic.JsonCodec
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.application.ApplicationValidator
import io.hydrosphere.serving.manager.domain.application.graph.VersionGraphComposer.VersionPipeline
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion

trait VersionGraphComposer {
  def compose(
      nodes: NonEmptyList[Node[ModelVersion.Internal]]
  ): Either[DomainError, VersionPipeline]
}

object VersionGraphComposer {

  @JsonCodec
  case class PipelineStage(
      modelVariants: NonEmptyList[Variant[ModelVersion.Internal]],
      signature: ModelSignature
  )

  @JsonCodec
  case class VersionPipeline(stages: NonEmptyList[PipelineStage], pipelineSignature: ModelSignature)

  def default: VersionGraphComposer = {
    new VersionGraphComposer {
      override def compose(
          nodes: NonEmptyList[Node[ModelVersion.Internal]]
      ): Either[DomainError, VersionPipeline] = {
        nodes match {
          case NonEmptyList(singleStage, Nil) if singleStage.variants.length == 1 =>
            inferSimpleApp(singleStage.variants.head) // don't perform checks
          case stages =>
            inferPipelineApp(stages)
        }
      }

      private def inferSimpleApp(
          version: Variant[ModelVersion.Internal]
      ): Either[DomainError, VersionPipeline] = {
        for {
          signature <- Either.fromOption(
            version.item.modelContract.predict,
            DomainError.notFound(s"Can't find predict signature for model ${version.item.fullName}")
          )
        } yield {
          val stages = NonEmptyList.of(
            PipelineStage(
              modelVariants = NonEmptyList.of(Variant(version.item, 100)), // 100 since this is the only service in the app
              signature = signature
            )
          )
          VersionPipeline(stages, signature)
        }
      }

      private def inferPipelineApp(
          stages: NonEmptyList[Node[ModelVersion.Internal]]
      ): Either[DomainError, VersionPipeline] = {
        val parsedStages = stages.traverse { stage =>
          val stageWeight = stage.variants.map(_.weight).foldLeft(0)(_ + _)
          if (stageWeight == 100) {
            for {
              stageSig <- ApplicationValidator
                .inferStageSignature(stage.variants.map(_.item).toList)
            } yield {
              PipelineStage(modelVariants = stage.variants, signature = stageSig)
            }
          } else {
            Left(
              DomainError
                .invalidRequest(s"Sum of weights must equal 100. Current sum: $stageWeight")
            )
          }
        }
        parsedStages.map { s =>
          val signature = ModelSignature(
            signatureName = "INFERRED_PIPELINE_SIGNATURE",
            inputs = s.head.signature.inputs,
            outputs = s.last.signature.outputs
          )
          VersionPipeline(s, signature)
        }
      }

    }
  }
}
