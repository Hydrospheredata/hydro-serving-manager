package io.hydrosphere.serving.manager.domain.clouddriver.k8s

import io.hydrosphere.serving.manager.domain.clouddriver.{
  CloudInstanceEvent,
  ServableEvent,
  ServableStates
}
import cats.implicits._
import cats.effect._
import cats.effect.concurrent.Ref

class K8sServableStates[F[_]: Sync](ref: Ref[F, Map[String, K8sServableState]])
    extends ServableStates[F] {
  override def handleEvent(event: CloudInstanceEvent): F[ServableEvent] =
    for {
      state <- ref.get
      servableState =
        state.getOrElse[K8sServableState](event.instanceName, K8sServableState.default)
      newServableState = event match {
        case ReplicaSetIsFailed(_, msg)       => servableState.copy(rs = RsIsFailed(msg))
        case ReplicaSetIsOk(_, msg)           => servableState.copy(rs = RsIsOk(msg))
        case ServiceIsAvailable(_)            => servableState.copy(svc = SvcIsAvailable)
        case ServiceIsUnavailable(_, message) => servableState.copy(svc = SvcIsUnavailable(message))
        case _                                => servableState
      }
      _ <- ref.update(_ + (event.instanceName -> newServableState))
      servableEvent = K8sServableState.toServableEvent(newServableState)
    } yield servableEvent
}
