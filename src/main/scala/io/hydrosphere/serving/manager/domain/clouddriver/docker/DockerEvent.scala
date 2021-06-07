package io.hydrosphere.serving.manager.domain.clouddriver.docker

import io.hydrosphere.serving.manager.domain.clouddriver.CloudInstanceEvent

trait DockerEvent extends CloudInstanceEvent {
  override def instanceName: String
}

final case class Create(instanceName: String)                extends DockerEvent
final case class Start(instanceName: String)                 extends DockerEvent
final case class Stop(instanceName: String, message: String) extends DockerEvent
