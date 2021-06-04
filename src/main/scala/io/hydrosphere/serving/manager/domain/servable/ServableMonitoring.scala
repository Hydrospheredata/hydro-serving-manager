package io.hydrosphere.serving.manager.domain.servable

import cats.data.OptionT
import cats.implicits._
import cats.effect._
import cats.effect.implicits._
import io.hydrosphere.serving.manager.domain.clouddriver.docker.DockerEvent
import io.hydrosphere.serving.manager.domain.clouddriver.k8s.K8sEvent
import io.hydrosphere.serving.manager.domain.clouddriver.{
  CloudDriver,
  ServableEvent,
  ServableNotReady,
  ServableReady,
  ServableStarting,
  ServableStates
}
import io.hydrosphere.serving.manager.domain.servable.Servable.Status.{
  NotServing,
  Serving,
  Starting
}
import org.apache.logging.log4j.scala.Logging

trait ServableMonitoring[F[_]] {
  def start(): F[Fiber[F, Unit]]
}

object ServableMonitoring extends Logging {
  def make[F[_]](
      cloudDriver: CloudDriver[F],
      repo: ServableRepository[F],
      servableStates: ServableStates[F]
  )(implicit F: Concurrent[F]): ServableMonitoring[F] =
    new ServableMonitoring[F] {
      override def start(): F[Fiber[F, Unit]] = {
        logger.info("Servable stream has been started")
        stream.start
      }

      def stream: F[Unit] =
        cloudDriver.getEvents
          .evalTap { cloudInstanceEvent =>
            val effect = for {
              servable  <- OptionT(repo.get(cloudInstanceEvent.instanceName))
              servEvent <- OptionT.liftF(servableStates.handleEvent(cloudInstanceEvent))
              _         <- OptionT.liftF(repo.upsert(updatedServable(servable, servEvent)))
            } yield ()

            effect.value.as(())
          }
          .onFinalizeCase {
            case ExitCase.Completed =>
              for {
                _        <- streamFinishMessage("completed")
                reStream <- stream
              } yield reStream
            case ExitCase.Error(e) =>
              for {
                _        <- streamFinishMessage("finished with error " + e)
                reStream <- stream
              } yield reStream
            case ExitCase.Canceled => streamFinishMessage("cancelled")
          }
          .compile
          .drain

      def updatedServable(servable: Servable, servEvent: ServableEvent): Servable =
        servEvent match {
          case ServableNotReady(message) =>
            servable.copy(status = NotServing, message = message.some)
          case ServableReady(message) =>
            servable.copy(status = Serving, message = message)
          case ServableStarting =>
            servable.copy(status = Starting, message = None)
          case _ => servable
        }

      private def streamFinishMessage(msg: String): F[Unit] =
        F.delay(logger.info("Servable monitoring stream was " + msg))
    }
}
