package io.hydrosphere.serving.manager.domain.application

import cats.data.NonEmptyList
import cats.implicits._
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.domain.DomainError


object GraphComposer {
  type Result = (ApplicationGraph, ModelSignature)

  final val INFERRED_PIPELINE_SIGNATURE = "INFERRED_PIPELINE_SIGNATURE"

  def compose(
    nodes: NonEmptyList[NonEmptyList[ApplicationServable]]
  ): Either[DomainError, Result] = {
    nodes match {
      case NonEmptyList(NonEmptyList(singleStage, Nil), Nil) =>
        inferSimpleApp(singleStage) // don't perform checks
      case stages =>
        inferPipelineApp(stages)
    }
  }

  def inferSimpleApp(
    version: ApplicationServable
  ): Either[DomainError, Result] = {
    for {
      signature <- Either.fromOption(
        version.modelVersion.modelContract.predict,
        DomainError.notFound(s"Can't find predict signature for model ${version.modelVersion.fullName}")
      )
    } yield {
      val stages = NonEmptyList.of(
        ApplicationStage(
          variants = NonEmptyList.of(version.copy(weight = 100)),
          signature = signature
        )
      )
      ApplicationGraph(stages) -> signature
    }
  }

  def inferPipelineApp(
    stages: NonEmptyList[NonEmptyList[ApplicationServable]]
  ): Either[DomainError, Result] = {
    val parsedStages = stages.traverse { stage =>
      val stageWeight = stage.map(_.weight).foldLeft(0)(_ + _)
      if (stageWeight == 100) {
        for {
          stageSig <- ApplicationValidator
            .inferStageSignature(stage.map(_.modelVersion).toList)
        } yield ApplicationStage(variants = stage, signature = stageSig)
      } else {
        Left(
          DomainError
            .invalidRequest(s"Sum of weights must equal 100. Current sum: $stageWeight")
        )
      }
    }
    parsedStages.map { s =>
      val signature = ModelSignature(signatureName = INFERRED_PIPELINE_SIGNATURE,
        inputs = s.head.signature.inputs,
        outputs = s.last.signature.outputs)
      ApplicationGraph(s) -> signature
    }
  }
}