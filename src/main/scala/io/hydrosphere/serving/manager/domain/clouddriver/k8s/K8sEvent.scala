package io.hydrosphere.serving.manager.domain.clouddriver.k8s

import io.hydrosphere.serving.manager.domain.clouddriver.CloudInstanceEvent

trait K8sEvent extends CloudInstanceEvent {
  override def instanceName: String
}

final case class ServiceIsAvailable(instanceName: String)                    extends K8sEvent
final case class ServiceIsUnavailable(instanceName: String, message: String) extends K8sEvent
final case class ReplicaSetIsOk(instanceName: String, message: Option[String] = None)
    extends K8sEvent
final case class ReplicaSetIsFailed(instanceName: String, message: String) extends K8sEvent
