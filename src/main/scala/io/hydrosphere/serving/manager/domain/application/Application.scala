package io.hydrosphere.serving.manager.domain.application

import cats.data.NonEmptyList
import cats.implicits._
import enumeratum.{CirceEnum, Enum, EnumEntry}
import io.circe.generic.JsonCodec

import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfiguration
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableStatusComposer}
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.domain.servable.Servable.{Status => ServableStatus}

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
    signature: Signature,
    kafkaStreaming: List[ApplicationKafkaStream],
    graph: ApplicationGraph,
    metadata: Map[String, String] = Map.empty
) {
  lazy val servables: List[Servable] =
    graph.stages.flatMap(s => s.variants.map(_.servable)).toList.flatten
  lazy val statusAndMessage: (List[String], ServableStatus) =
    ServableStatusComposer.combineStatuses(servables)

  val status: Application.Status = statusAndMessage._2 match {
    case ServableStatus.Serving      => Application.Status.Ready
    case ServableStatus.NotServing   => Application.Status.Failed
    case ServableStatus.NotAvailable => Application.Status.Failed
    case ServableStatus.Starting     => Application.Status.Assembling
  }

  val statusMessage: Option[String] = statusAndMessage._1.combineAll.some

}

object Application {
  sealed trait Status extends EnumEntry

  case object Status extends Enum[Status] with CirceEnum[Status] {

    case object Assembling extends Status

    case object Failed extends Status

    case object Ready extends Status

    override def values: IndexedSeq[Status] = findValues
  }
}
