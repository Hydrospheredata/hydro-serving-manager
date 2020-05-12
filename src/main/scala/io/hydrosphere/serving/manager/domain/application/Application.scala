package io.hydrosphere.serving.manager.domain.application

import cats.data.NonEmptyList
import enumeratum.{CirceEnum, Enum, EnumEntry}
import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.Servable

@JsonCodec
case class Variant(
    modelVersion: ModelVersion.Internal,
    servable: Option[Servable],
    weight: Int
)

@JsonCodec
case class WeightedNode(variants: NonEmptyList[Variant], signature: Signature)

@JsonCodec
case class ApplicationGraph(
    nodes: NonEmptyList[WeightedNode],
    signature: Signature
)

@JsonCodec
case class Application(
    id: Long,
    name: String,
    namespace: Option[String],
    status: Application.Status,
    signature: Signature,
    kafkaStreaming: List[ApplicationKafkaStream],
    executionGraph: ApplicationGraph,
    message: String,
    metadata: Map[String, String] = Map.empty
)

object Application {
  sealed trait Status extends EnumEntry

  case object Status extends Enum[Status] with CirceEnum[Status] {

    case object Assembling extends Status

    case object Failed extends Status

    case object Ready extends Status

    override def values: IndexedSeq[Status] = findValues
  }
}
