package io.hydrosphere.serving.manager.domain.servable

import cats.data.OptionT
import cats.effect.{Async, Timer}
import cats.implicits._
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.clouddriver.{CloudDriver, CloudInstance}
import io.hydrosphere.serving.manager.infrastructure.grpc.PredictionClient
import io.hydrosphere.serving.tensorflow.api.prediction_service.StatusResponse.ServiceStatus._
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.duration._

trait ServableMonitor[F[_]] {
  def monitor(name: String): F[Servable.Status]
}

object ServableMonitor extends Logging {
  def default[F[_]](
    clientCtor: PredictionClient.Factory[F],
    cloudDriver: CloudDriver[F],
    firstPingSleep: FiniteDuration,
    maxPingSleep: FiniteDuration,
    driverSleep: FiniteDuration
  )(implicit F: Async[F], timer: Timer[F]): ServableMonitor[F] = {
    new ServableMonitor[F] {

      def pping(client: PredictionClient[F], sleepSeconds: Double): F[(String, Int) => Servable.Status] = {
        def retry() = {
          timer.sleep(sleepSeconds.seconds) >> pping(client, sleepSeconds * 2)
        }

        for {
          resp <- client.status().attempt
          _ <- F.delay(logger.info(s"Sleep duration $sleepSeconds"))
          res <- resp match {
            case Left(error) if sleepSeconds >= maxPingSleep.toSeconds => F.pure((h: String, p: Int) => Servable.NotAvailable(error.getMessage, Some(h), Some(p)))
            // this error could be a result of late initialization. Better to retry, just to be sure.
            case Left(value) => retry()
            case Right(response) => response.status match {
              case SERVING => F.pure(Servable.Serving(response.message, _, _))
              // Servable said that it is in invalid state. Response message probably contains some useful data.
              case NOT_SERVING => F.pure(Servable.NotServing(response.message, _, _))
              // Sleep timeout. Consider Servable unreachable
              case x if sleepSeconds > maxPingSleep.toSeconds => F.pure((h: String, p: Int) => Servable.NotAvailable(response.message, Some(h), Some(p)))
              // UNKNOWN status is assigned to Servable initialization. Retry.
              case UNKNOWN => retry()
              // any other unexpected statuses are considered NotAvailable
              case _ => F.pure((h: String, p: Int) => Servable.NotAvailable(response.message, Some(h), Some(p)))
            }
          }
        } yield res
      }

      def ping(host: String, port: Int): F[Servable.Status] = {
        clientCtor.make(host, port).use { client =>
          for {
            sCtor <- pping(client, firstPingSleep.toSeconds)
          } yield sCtor(host, port)
        }
      }

      def monitor(name: String): F[Servable.Status] = {
        for {
          c <- OptionT(cloudDriver.instance(name))
            .getOrElseF(F.raiseError(DomainError.internalError(s"Servable $name doesn't exist")))
          x <- c.status match {
            case CloudInstance.Status.Running(host, port) =>
              ping(host, port)
            case CloudInstance.Status.Stopped =>
              F.raiseError[Servable.Status](DomainError.internalError(s"Servable $name stopped unexpectedly"))
            case CloudInstance.Status.Starting =>
              timer.sleep(driverSleep) >> monitor(name)
          }
        } yield x
      }
    }
  }
}