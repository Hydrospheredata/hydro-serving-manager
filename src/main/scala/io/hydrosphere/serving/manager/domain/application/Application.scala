package io.hydrosphere.serving.manager.domain.application

import cats.data.NonEmptyList
import io.circe.generic.JsonCodec
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}
import io.hydrosphere.serving.manager.domain.application.graph.ExecutionNode
import io.hydrosphere.serving.manager.domain.application.graph.VersionGraphComposer.PipelineStage
import io.hydrosphere.serving.manager.domain.contract.Signature

@JsonCodec
case class Application[+T <: Application.Status](
    id: Long,
    name: String,
    namespace: Option[String],
    status: T,
    signature: Signature,
    kafkaStreaming: List[ApplicationKafkaStream],
    versionGraph: NonEmptyList[PipelineStage],
    metadata: Map[String, String] = Map.empty
)

object Application {
  private implicit val config = Configuration.default.withDiscriminator("status")

  @ConfiguredJsonCodec
  sealed trait Status                                   extends Product with Serializable
  case object Assembling                                extends Status
  case class Failed(reason: Option[String])             extends Status
  case class Ready(stages: NonEmptyList[ExecutionNode]) extends Status

  type GenericApplication = Application[Status]
  type AssemblingApp      = Application[Assembling.type]
  type FailedApp          = Application[Failed]
  type ReadyApp           = Application[Ready]
}
