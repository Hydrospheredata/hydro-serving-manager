package io.hydrosphere.serving.manager.domain.application

import cats.data.NonEmptyList
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfiguration
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.Servable.GenericServable

case class ApplicationKafkaStream(
  sourceTopic: String,
  destinationTopic: String,
  consumerId: Option[String],
  errorTopic: Option[String]
)

case class ApplicationServable(
  modelVersion: ModelVersion.Internal,
  weight: Int,
  servable: Option[GenericServable] = None,
  requiredDeploymentConfig: Option[DeploymentConfiguration] = None
)
case class ApplicationStage(variants: NonEmptyList[ApplicationServable], signature: ModelSignature)

case class ApplicationGraph(stages: NonEmptyList[ApplicationStage])

case class Application(
  id: Long,
  name: String,
  namespace: Option[String],
  status: Application.Status,
  statusMessage: Option[String] = None,
  signature: ModelSignature,
  kafkaStreaming: List[ApplicationKafkaStream],
  graph: ApplicationGraph,
  metadata: Map[String, String] = Map.empty
)

object Application {
  sealed trait Status extends Product with Serializable

  object Status {
    def fromString(name: String): Option[Status] = {
      name match {
        case "Assembling" => Some(Application.Assembling)
        case "Ready" => Some(Application.Ready)
        case "Failed" => Some(Application.Failed)
        case _ => None
      }
    }
  }

  case object Assembling extends Status

  case object Failed extends Status

  case object Ready extends Status
}
