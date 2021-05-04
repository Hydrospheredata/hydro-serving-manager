package io.hydrosphere.serving.manager.domain.servable

import cats.data.OptionT
import cats.implicits._
import cats.effect._
import cats.effect.implicits._
import io.hydrosphere.serving.manager.domain.servable.Servable.{Status => ServableStatus}
import io.hydrosphere.serving.manager.domain.clouddriver.{
  Available,
  CloudDriver,
  CloudInstanceEvent,
  NotAvailable,
  NotServing,
  Ready,
  Starting
}
import org.apache.logging.log4j.scala.Logging

trait ServableMonitoring[F[_]] {
  def start(): F[Fiber[F, Unit]]
}

object ServableMonitoring extends Logging {
  def make[F[_]](
      cloudDriver: CloudDriver[F],
      repo: ServableRepository[F]
  )(implicit F: Concurrent[F]): ServableMonitoring[F] =
    new ServableMonitoring[F] {
      override def start(): F[Fiber[F, Unit]] = {
        logger.info("Servable monitoring has been started")
        cloudDriver.getEvents
          .evalTap { servEvent =>
            val effect = for {
              servable <- OptionT(repo.get(servEvent.instanceName))
              _        <- OptionT.liftF(repo.upsert(updatedServable(servable, servEvent)))
            } yield ()

            effect.value.as(())
          }
          .compile
          .drain
          .start
      }

      def updatedServable(servable: Servable, servEvent: CloudInstanceEvent): Servable = {
        val (newStatus, message) = getNewStatus(servable.status, servable.message, servEvent)
        servable.copy(status = newStatus, message = message)
      }

      def getNewStatus(
          prevStatus: ServableStatus,
          prevMessage: Option[String],
          eventType: CloudInstanceEvent
      ): (ServableStatus, Option[String]) =
        (prevStatus, eventType) match {
          case (ServableStatus.NotAvailable, Available(_)) => (ServableStatus.Serving, none)
          case (ServableStatus.NotAvailable, Ready(_, _)) =>
            (ServableStatus.NotAvailable, prevMessage)
          case (ServableStatus.NotAvailable, NotServing(_, _)) =>
            (ServableStatus.NotAvailable, prevMessage)
          case (ServableStatus.NotServing, NotAvailable(_, _)) =>
            (ServableStatus.NotAvailable, prevMessage)
          case (_, Available(_))             => (ServableStatus.Serving, none)
          case (_, Starting(_, warning))     => (ServableStatus.Starting, warning)
          case (_, NotAvailable(_, message)) => (ServableStatus.NotAvailable, message.some)
          case (_, NotServing(_, message))   => (ServableStatus.NotServing, message.some)
          case (_, Ready(_, warning))        => (ServableStatus.Serving, warning)
        }
    }
}
