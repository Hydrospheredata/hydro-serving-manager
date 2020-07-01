package io.hydrosphere.serving.manager.domain

import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol._
import io.hydrosphere.serving.manager.infrastructure.protocol.PlayJsonAdapter.formatAdapter
import skuber.Pod.{Affinity, Toleration}
import spray.json.RootJsonFormat
import skuber.json.format._
import io.hydrosphere.serving.manager.infrastructure.protocol.PlayJsonAdapter._

package object deploy_config {
  type NodeSelector = Map[String, String]

  type CpuDefinition = String

  type MemoryDefinition = String

  final case class Requirement(cpu: String, memory: String)

  object Requirement {
    implicit val format: RootJsonFormat[Requirement] = jsonFormat2(Requirement.apply)
  }

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
    requirements: Option[Requirements],
  )

  final case class K8sPodConfig(
    nodeSelector: Option[NodeSelector],
    affinity: Option[Affinity],
    tolerations: List[Toleration],
  )

  final case class K8sDeploymentConfig(
    replicaCount: Option[Int]
  )

  final case class DeploymentConfiguration(
    name: String,
    container: Option[K8sContainerConfig],
    pod: Option[K8sPodConfig],
    deployment: Option[K8sDeploymentConfig],
    hpa: Option[K8sHorizontalPodAutoscalerConfig],
  )

  object DeploymentConfiguration {
    implicit val aff: RootJsonFormat[Affinity] = formatAdapter[Affinity]
    implicit val tol: RootJsonFormat[Toleration] = formatAdapter[Toleration]
    implicit val format: RootJsonFormat[DeploymentConfiguration] = jsonFormat5(DeploymentConfiguration.apply)
  }

}