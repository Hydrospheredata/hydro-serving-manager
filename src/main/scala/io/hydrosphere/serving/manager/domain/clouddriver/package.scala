package io.hydrosphere.serving.manager.domain

import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol._
import io.hydrosphere.serving.manager.infrastructure.protocol.PlayJsonAdapter._
import skuber.Pod.{Affinity, Toleration}
import skuber.json.format._
import spray.json.RootJsonFormat

package object clouddriver {
  type NodeSelector = Map[String, String]

  type CpuDefinition = String

  type MemoryDefinition = String

  final case class Requirement(cpu: String, memory: String)
  object Requirement {
    implicit val format: RootJsonFormat[Requirement] = jsonFormat2(Requirement.apply)
  }

  final case class Requirements(limits: Requirement, requests: Requirement)
  object Requirements {
    implicit val format: RootJsonFormat[Requirements] = jsonFormat2(Requirements.apply)
  }

  final case class HorizontalPodAutoscalerConfig(
    minReplicas: Option[Int] = Some(1),
    maxReplicas: Int = 1,
    cpuUtilization: Option[Int] = None
  )
  object HorizontalPodAutoscalerConfig {
    implicit val format: RootJsonFormat[HorizontalPodAutoscalerConfig] = jsonFormat3(HorizontalPodAutoscalerConfig.apply)
  }

  final case class DeploymentConfiguration(
    requirements: Requirements,
    nodeSelector: NodeSelector,
    affinity: Affinity,
    tolerations: List[Toleration],
    replicaCount: Int
  )
  object DeploymentConfiguration {
    implicit val aff: RootJsonFormat[Affinity] = formatAdapter[Affinity]
    implicit val tol: RootJsonFormat[Toleration] = formatAdapter[Toleration]
    implicit val format: RootJsonFormat[DeploymentConfiguration] = jsonFormat5(DeploymentConfiguration.apply)
  }

  final case class CloudResourceConfiguration(
    hpa: Option[HorizontalPodAutoscalerConfig],
    deployment: Option[DeploymentConfiguration]
  )

  object CloudResourceConfiguration {
    implicit val format: RootJsonFormat[CloudResourceConfiguration] = jsonFormat2(CloudResourceConfiguration.apply)
  }
}