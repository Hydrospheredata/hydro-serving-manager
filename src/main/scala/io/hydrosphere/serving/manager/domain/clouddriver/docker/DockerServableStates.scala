package io.hydrosphere.serving.manager.domain.clouddriver.docker

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import io.hydrosphere.serving.manager.domain.clouddriver.{
  CloudInstanceEvent,
  ServableEvent,
  ServableStates
}

class DockerServableStates[F[_]: Sync](ref: Ref[F, Map[String, DockerServableState]])
    extends ServableStates[F] {
  override def handleEvent(event: CloudInstanceEvent): F[ServableEvent] =
    for {
      state <- ref.get
      servableState =
        state.getOrElse[DockerServableState](event.instanceName, DockerServableState.default)
      newState = event match {
        case Create(_)        => DockerServableState(ContainerReady)
        case Start(_)         => DockerServableState(ContainerStarted)
        case Stop(_, message) => DockerServableState(ContainerStopped(message))
        case _                => servableState
      }
      _ <- ref.update(_ + (event.instanceName -> newState))
      evt = DockerServableState.toServableEvent(newState.status)
    } yield evt
}
