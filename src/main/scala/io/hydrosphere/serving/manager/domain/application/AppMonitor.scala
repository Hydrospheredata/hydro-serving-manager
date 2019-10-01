package io.hydrosphere.serving.manager.domain.application

import cats.Monad
import cats.effect.Concurrent
import cats.implicits._
import io.hydrosphere.serving.manager.discovery.{ApplicationPublisher, DiscoveryEvent, ServableSubscriber}
import io.hydrosphere.serving.manager.domain.servable.Servable

object AppMonitor {
  def monitor[F](
    appRepo: ApplicationRepository[F],
    servableSubscriber: ServableSubscriber[F],
  )(implicit
  F: Concurrent[F]
  ) = {
    for {
      event <- servableSubscriber.subscribe
      apps <- event match {
        case DiscoveryEvent.ItemUpdate(items) =>
          val r = fs2.Stream.emits[F, Servable](items).flatMap(s => appRepo.findServableUsage(s.fullName))
          r
          ???
        case _ => ???
      }
    } yield ()
  }
}
