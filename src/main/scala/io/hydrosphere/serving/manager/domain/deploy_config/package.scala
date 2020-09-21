package io.hydrosphere.serving.manager.domain

import io.hydrosphere.serving.manager.discovery.DiscoveryService
import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol._
import io.hydrosphere.serving.manager.infrastructure.protocol.PlayJsonAdapter._
import skuber.Pod.{Affinity, Toleration}
import spray.json.RootJsonFormat
import skuber.json.format._

package object deploy_config {
  type NodeSelector = Map[String, String]
  type Requirement = Map[String, String]
  type EnvironmentVariables = Map[String, String]

  final case class Requirements(limits: Option[Requirement], requests: Option[Requirement])

  object Requirements {
    implicit val format: RootJsonFormat[Requirements] = jsonFormat2(Requirements.apply)
  }

  final case class K8sHorizontalPodAutoscalerConfig(
    minReplicas: Option[Int] = Some(1),
    maxReplicas: Int = 1,
    cpuUtilization: Option[Int] = None
  )

  object K8sHorizontalPodAutoscalerConfig {
    implicit val format: RootJsonFormat[K8sHorizontalPodAutoscalerConfig] = jsonFormat3(K8sHorizontalPodAutoscalerConfig.apply)
  }

  final case class K8sContainerConfig(
    resources: Option[Requirements],
    env: Option[EnvironmentVariables]
  )

  object K8sContainerConfig {
    implicit val format: RootJsonFormat[K8sContainerConfig] = jsonFormat2(K8sContainerConfig.apply)
  }

  final case class K8sPodConfig(
    nodeSelector: Option[NodeSelector],
    affinity: Option[Affinity],
    tolerations: List[Toleration],
  )

  object K8sPodConfig {
    implicit val aff: RootJsonFormat[Affinity] = formatAdapter[Affinity]
    implicit val tol: RootJsonFormat[Toleration] = formatAdapter[Toleration]
    implicit val format: RootJsonFormat[K8sPodConfig] = jsonFormat3(K8sPodConfig.apply)
  }

  final case class K8sDeploymentConfig(
    replicaCount: Option[Int]
  )

  object K8sDeploymentConfig {
    implicit val format: RootJsonFormat[K8sDeploymentConfig] = jsonFormat1(K8sDeploymentConfig.apply)
  }

  final case class DeploymentConfiguration(
    name: String,
    container: Option[K8sContainerConfig],
    pod: Option[K8sPodConfig],
    deployment: Option[K8sDeploymentConfig],
    hpa: Option[K8sHorizontalPodAutoscalerConfig],
  )

  object DeploymentConfiguration {
    implicit val format: RootJsonFormat[DeploymentConfiguration] = jsonFormat5(DeploymentConfiguration.apply)
  }

  object DeploymentConfigurationEvents extends DiscoveryService[DeploymentConfiguration, String]
}