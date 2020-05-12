package io.hydrosphere.serving.manager.domain.application.graph

import cats.data.NonEmptyList
import cats.implicits._
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.application.{
  ApplicationGraph,
  ApplicationValidator,
  Variant,
  WeightedNode
}
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion

object VersionGraphComposer {

  def compose(
      nodes: NonEmptyList[NonEmptyList[Variant]]
  ): Either[DomainError, ApplicationGraph] =
    nodes match {
      case NonEmptyList(singleStage, Nil) if singleStage.length == 1 =>
        inferSimpleApp(singleStage.head.modelVersion).asRight
      case stages =>
        inferPipelineApp(stages)
    }

  private def inferSimpleApp(
      version: ModelVersion.Internal
  ) = {
    val signature = version.modelContract.predict
    val stages = NonEmptyList.of(
      WeightedNode(
        variants = NonEmptyList.of(
          Variant(version, None, 100)
        ), // 100 since this is the only service in the app
        signature = version.modelContract.predict
      )
    )
    ApplicationGraph(stages, version.modelContract.predict)
  }

  private def inferPipelineApp(
      stages: NonEmptyList[NonEmptyList[Variant]]
  ): Either[DomainError, ApplicationGraph] = {
    val parsedStages = stages.traverse { stage =>
      val stageWeight = stage.map(_.weight).foldLeft(0)(_ + _)
      if (stageWeight == 100)
        for {
          stageSig <-
            ApplicationValidator
              .inferStageSignature(stage.map(_.modelVersion))
        } yield WeightedNode(variants = stage, signature = stageSig)
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
      ApplicationGraph(s, signature)
    }
  }
}
