package io.hydrosphere.serving.manager.domain.application

import cats.data.NonEmptyList
import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfiguration
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.circe.generic.JsonCodec
import enumeratum.{CirceEnum, Enum, EnumEntry}
import io.hydrosphere.serving.manager.domain.contract.Signature

@JsonCodec
case class ApplicationKafkaStream(
    sourceTopic: String,
    destinationTopic: String,
    consumerId: Option[String],
    errorTopic: Option[String]
)

@JsonCodec
case class ApplicationServable(
    modelVersion: ModelVersion.Internal,
    weight: Int,
    servable: Option[Servable] = None,
    requiredDeploymentConfig: Option[DeploymentConfiguration] = None
)
@JsonCodec
case class ApplicationStage(variants: NonEmptyList[ApplicationServable], signature: Signature)

@JsonCodec
case class ApplicationGraph(stages: NonEmptyList[ApplicationStage])

@JsonCodec
case class Application(
    id: Long,
    name: String,
    namespace: Option[String],
    status: Application.Status,
    statusMessage: Option[String] = None,
    signature: Signature,
    kafkaStreaming: List[ApplicationKafkaStream],
    graph: ApplicationGraph,
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
