package io.hydrosphere.serving.manager.domain.application

import cats.data.NonEmptyList
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.domain.application.graph.ExecutionNode
import io.hydrosphere.serving.manager.domain.application.graph.VersionGraphComposer.PipelineStage

case class Application[+T <: Application.Status](
  id: Long,
  name: String,
  namespace: Option[String],
  status: T,
  signature: ModelSignature,
  kafkaStreaming: List[ApplicationKafkaStream]
) {
  def generic: Application.GenericApplication = this
}

object Application {

  type GenericApplication = Application[Status]

  sealed trait Status

  case class Assembling(versionGraph: NonEmptyList[PipelineStage]) extends Status

  case class Failed(versionGraph: NonEmptyList[PipelineStage], reason: Option[String]) extends Status

  case class Ready(stages: NonEmptyList[ExecutionNode]) extends Status

  type AssemblingApp = Application[Assembling]
  type FailedApp     = Application[Failed]
  type ReadyApp      = Application[Ready]

}
