package io.hydrosphere.serving.manager.discovery.application

import io.hydrosphere.serving.manager.grpc.entities.ServingApp

sealed trait ApplicationEvent
object ApplicationEvent {
  final case class Removed(id: Long) extends ApplicationEvent
  final case class Started(app: ServingApp) extends ApplicationEvent
}

