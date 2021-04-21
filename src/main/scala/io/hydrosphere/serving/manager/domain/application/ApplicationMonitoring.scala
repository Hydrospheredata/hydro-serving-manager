package io.hydrosphere.serving.manager.domain.application

import cats.implicits._
import cats.effect.implicits._
import cats.effect.{Concurrent, Fiber}
import io.hydrosphere.serving.manager.discovery.DiscoveryEvent
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableEvents}
import org.apache.logging.log4j.scala.Logging

trait ApplicationMonitoring[F[_]] {
  def start(): F[Fiber[F, Unit]]
}

object ApplicationMonitoring extends Logging {
  def make[F[_]](
      servablePub: ServableEvents.Subscriber[F],
      appRepo: ApplicationRepository[F]
  )(implicit F: Concurrent[F]): ApplicationMonitoring[F] =
    new ApplicationMonitoring[F] {
      override def start(): F[Fiber[F, Unit]] = {
        logger.info("Application monitoring has been started")
        servablePub.subscribe.evalMap(updateApp).compile.drain.start
      }

      // TODO: Redundant update
      def updateApp(event: DiscoveryEvent[Servable, String]): F[Unit] =
        event match {
          case DiscoveryEvent.Initial => F.unit
          case DiscoveryEvent.ItemUpdate(items) =>
            items
              .traverse { servable =>
                appRepo.findServableUsage(servable.name).flatMap { apps =>
                  apps.traverse(app => appRepo.update(app))
                }
              }
              .as(())
          case DiscoveryEvent.ItemRemove(servables) =>
            servables
              .traverse { servable =>
                appRepo.findServableUsage(servableName = servable).flatMap { apps =>
                  apps.traverse(app => appRepo.update(app.copy()))
                }
              }
              .as(())
        }
    }
}
