package io.hydrosphere.serving.manager.discovery.servable

import cats.effect.Sync
import io.grpc.stub.StreamObserver
import io.hydrosphere.serving.discovery.serving.ServableDiscoveryEvent
import io.hydrosphere.serving.manager.grpc.entities.Servable


trait ServableObserver[F[_]] {
  def init(servables: List[Servable]): F[Unit]
  def notify(event: ServableDiscoveryEvent): F[Unit]
}

object ServableObserver {

  def grpc[F[_]](observer: StreamObserver[ServableDiscoveryEvent])(implicit F: Sync[F]): ServableObserver[F] = {
    new ServableObserver[F] {

      override def notify(event: ServableDiscoveryEvent): F[Unit] = F.delay {
        observer.onNext(event)
      }

      override def init(servables: List[Servable]): F[Unit] = notify(ServableDiscoveryEvent(added = servables))
    }
  }

}
