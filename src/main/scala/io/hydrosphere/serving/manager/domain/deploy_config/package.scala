package io.hydrosphere.serving.manager.domain

import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.discovery.DiscoveryService
import skuber.Pod.{Affinity, Toleration}
import skuber.json.format._
import io.hydrosphere.serving.manager.infrastructure.protocol.PlayJsonAdapter._

package object deploy_config {

  type NodeSelector         = Map[String, String]
  type Requirement          = Map[String, String]
  type EnvironmentVariables = Map[String, String]

  @JsonCodec
  final case class Requirements(
      limits: Option[Requirement],
      requests: Option[Requirement]
  )

  @JsonCodec
  final case class K8sHorizontalPodAutoscalerConfig(
      minReplicas: Option[Int] = Some(1),
      maxReplicas: Int = 1,
      cpuUtilization: Option[Int] = None
  )

  @JsonCodec
  final case class K8sContainerConfig(
      resources: Option[Requirements],
      env: Option[EnvironmentVariables]
  )

  @JsonCodec
  final case class K8sPodConfig(
      nodeSelector: Option[NodeSelector],
      affinity: Option[Affinity],
      tolerations: List[Toleration]
  )

  object K8sPodConfig {
    implicit val affEncoder: Encoder[Affinity] = encoder[Affinity]
    implicit val affDecoder: Decoder[Affinity] = decoder[Affinity]
  }

  @JsonCodec
  final case class K8sDeploymentConfig(
      replicaCount: Option[Int]
  )

  @JsonCodec
  final case class DeploymentConfiguration(
      name: String,
      container: Option[K8sContainerConfig],
      pod: Option[K8sPodConfig],
      deployment: Option[K8sDeploymentConfig],
      hpa: Option[K8sHorizontalPodAutoscalerConfig]
  )

  object DeploymentConfigurationEvents extends DiscoveryService[DeploymentConfiguration, String]
}
