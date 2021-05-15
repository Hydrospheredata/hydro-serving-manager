package io.hydrosphere.serving.manager.domain.servable

import cats.data.OptionT
import cats.implicits._
import cats.effect._
import cats.effect.implicits._
import cats.effect.kernel.Resource.ExitCase
import io.hydrosphere.serving.manager.domain.servable.Servable.{Status => ServableStatus}
import io.hydrosphere.serving.manager.domain.clouddriver._
import org.apache.logging.log4j.scala.Logging

trait ServableMonitoring[F[_]] {
  def start(): F[Fiber[F, Throwable, Unit]]
}

object ServableMonitoring extends Logging {
  def make[F[_]](
      cloudDriver: CloudDriver[F],
      repo: ServableRepository[F]
  )(implicit F: Async[F]): ServableMonitoring[F] =
    new ServableMonitoring[F] {
      override def start(): F[Fiber[F, Throwable, Unit]] = {
        logger.info("Servable monitoring has been started")
        cloudDriver.getEvents
          .evalTap { servEvent =>
            val effect = for {
              servable <- OptionT(repo.get(servEvent.instanceName))
              _        <- OptionT.liftF(repo.upsert(updatedServable(servable, servEvent)))
            } yield ()
            effect.value.void
          }
          .onFinalizeCase {
            case ExitCase.Succeeded  => streamFinishMessage("completed")
            case ExitCase.Errored(e) => streamFinishMessage("finished with error" + e)
            case ExitCase.Canceled   => streamFinishMessage("cancelled")
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
          case (_, Ready(_, warning))        => (ServableStatus.Serving, warning)
          case (_, Starting(_, warning))     => (ServableStatus.Starting, warning)
          case (_, NotAvailable(_, message)) => (ServableStatus.NotAvailable, message.some)
          case (_, NotServing(_, message))   => (ServableStatus.NotServing, message.some)
        }

      private def streamFinishMessage(msg: String) =
        F.delay(logger.info("Servable monitoring stream was " + msg))
    }
}
