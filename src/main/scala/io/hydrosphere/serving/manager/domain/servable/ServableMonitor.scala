package io.hydrosphere.serving.manager.domain.servable

import cats.effect.implicits._
import cats.effect.{Concurrent, Fiber, Timer}
import cats.implicits._
import io.hydrosphere.serving.manager.discovery.ServablePublisher
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.duration._


object ServableMonitor extends Logging {

  def databasePuller[F[_]](
    servableRepo: ServableRepository[F],
    servablePublisher: ServablePublisher[F],
    monitorSleep: FiniteDuration
  )(implicit
    F: Concurrent[F],
    timer: Timer[F],
    probe: ServableProbe[F],
  ): F[Fiber[F, Nothing]] = {
    val loopStep = for {
      step <- monitoringStep(servableRepo, servablePublisher)
        .handleError(x => logger.error(s"Error in monitoring loop", x))
      _ <- timer.sleep(monitorSleep)
    } yield step
    loopStep.foreverM.start
  }

  def monitoringStep[F[_]](
    repo: ServableRepository[F],
    pub: ServablePublisher[F]
  )(implicit
    F: Concurrent[F],
    probe: ServableProbe[F]
  ): F[Unit] = {
    for {
      servable <- repo.all()
      status <- probe.probe(servable)
        .handleError { x =>
          logger.error("Servable probe failed", x)
          Servable.NotAvailable(s"Probe failed. ${x.getMessage}", None, None)
        }
      _ <- F.delay(logger.debug(s"Probed: ${servable.fullName}"))
      _ <- if (servable.status != status) {
        val updated = servable.copy(status = status)
        repo.upsert(updated) >> pub.update(updated).void
      } else {
        F.unit
      }
    } yield ()
  }
}