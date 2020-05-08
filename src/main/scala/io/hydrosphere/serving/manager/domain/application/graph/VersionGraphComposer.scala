package io.hydrosphere.serving.manager.domain.application.graph

import cats.data.NonEmptyList
import cats.implicits._
import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.application.ApplicationValidator
import io.hydrosphere.serving.manager.domain.application.graph.VersionGraphComposer.VersionPipeline
import io.hydrosphere.serving.manager.domain.contract.Signature
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
      signature: Signature
  )

  @JsonCodec
  case class VersionPipeline(stages: NonEmptyList[PipelineStage], pipelineSignature: Signature)

  def default: VersionGraphComposer =
    new VersionGraphComposer {
      override def compose(
          nodes: NonEmptyList[Node[ModelVersion.Internal]]
      ): Either[DomainError, VersionPipeline] =
        nodes match {
          case NonEmptyList(singleStage, Nil) if singleStage.variants.length == 1 =>
            inferSimpleApp(singleStage.variants.head).asRight
          case stages =>
            inferPipelineApp(stages)
        }

      private def inferSimpleApp(
          version: Variant[ModelVersion.Internal]
      ): VersionPipeline = {
        val signature = version.item.modelContract.predict
        val stages = NonEmptyList.of(
          PipelineStage(
            modelVariants = NonEmptyList.of(
              Variant(version.item, 100)
            ), // 100 since this is the only service in the app
            signature = signature
          )
        )
        VersionPipeline(stages, signature)
      }

      private def inferPipelineApp(
          stages: NonEmptyList[Node[ModelVersion.Internal]]
      ): Either[DomainError, VersionPipeline] = {
        val parsedStages = stages.traverse { stage =>
          val stageWeight = stage.variants.map(_.weight).foldLeft(0)(_ + _)
          if (stageWeight == 100)
            for {
              stageSig <-
                ApplicationValidator
                  .inferStageSignature(stage.variants.map(_.item))
            } yield PipelineStage(modelVariants = stage.variants, signature = stageSig)
          else
            Left(
              DomainError
                .invalidRequest(s"Sum of weights must equal 100. Current sum: $stageWeight")
            )
        }
        parsedStages.map { s =>
          val signature = Signature(
            signatureName = "INFERRED_PIPELINE_SIGNATURE",
            inputs = s.head.signature.inputs,
            outputs = s.last.signature.outputs
          )
          VersionPipeline(s, signature)
        }
      }
    }
}
