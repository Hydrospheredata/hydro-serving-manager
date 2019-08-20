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
  kafkaStreaming: List[ApplicationKafkaStream],
  versionGraph: NonEmptyList[PipelineStage]
)

object Application {
  sealed trait Status extends Product with Serializable

  case object Assembling extends Status

  case class Failed(reason: Option[String]) extends Status

  case class Ready(stages: NonEmptyList[ExecutionNode]) extends Status

  type GenericApplication = Application[Status]
  type AssemblingApp = Application[Assembling.type]
  type FailedApp = Application[Failed]
  type ReadyApp = Application[Ready]
}
