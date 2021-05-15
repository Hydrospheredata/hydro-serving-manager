package io.hydrosphere.serving.manager.domain.application

import cats.implicits._
import cats.effect.implicits._
import cats.effect.{Concurrent, Fiber}
import io.hydrosphere.serving.manager.discovery.DiscoveryEvent
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableEvents}
import org.apache.logging.log4j.scala.Logging

trait ApplicationMonitoring[F[_]] {
  def start(): F[Fiber[F, Throwable, Unit]]
}

object ApplicationMonitoring extends Logging {
  def make[F[_]](
      servableSub: ServableEvents.Subscriber[F],
      appRepo: ApplicationRepository[F],
      appPub: ApplicationEvents.Publisher[F]
  )(implicit F: Concurrent[F]): ApplicationMonitoring[F] =
    new ApplicationMonitoring[F] {
      override def start(): F[Fiber[F, Throwable, Unit]] = {
        logger.info("Application monitoring has been started")
        servableSub.subscribe.evalMap(updateApp).compile.drain.start
      }

      def updateApp(event: DiscoveryEvent[Servable, String]): F[Unit] =
        event match {
          case DiscoveryEvent.ItemUpdate(servables) => updateApps(servables)
          case _                                    => F.unit
        }

      private def getApplications(servableNames: List[String]): F[List[Application]] =
        servableNames.traverse {
          appRepo.findServableUsage
        } map { _.flatten }

      private def updateApps(servables: List[Servable]): F[Unit] =
        for {
          names        <- F.pure(servables map { _.name })
          applications <- getApplications(names)
          _            <- applications traverse appPub.update
        } yield ()
    }
}
