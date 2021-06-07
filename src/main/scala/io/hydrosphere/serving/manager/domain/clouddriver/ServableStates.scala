package io.hydrosphere.serving.manager.domain.clouddriver

import cats.implicits._
import cats.effect.Sync
import cats.effect.concurrent.Ref
import io.hydrosphere.serving.manager.config.CloudDriverConfiguration
import io.hydrosphere.serving.manager.domain.clouddriver.docker.{
  DockerServableState,
  DockerServableStates
}
import io.hydrosphere.serving.manager.domain.clouddriver.k8s.{K8sServableState, K8sServableStates}

trait ServableStates[F[_]] {
  def handleEvent(event: CloudInstanceEvent): F[ServableEvent]
}

object ServableStates {
  def make[F[_]: Sync](config: CloudDriverConfiguration): F[ServableStates[F]] =
    config match {
      case _: CloudDriverConfiguration.Docker =>
        for {
          ref <- Ref[F].of(Map.empty[String, DockerServableState])
        } yield new DockerServableStates[F](ref)
      case _: CloudDriverConfiguration.Kubernetes =>
        for {
          ref <- Ref[F].of(Map.empty[String, K8sServableState])
        } yield new K8sServableStates[F](ref)
    }
}
