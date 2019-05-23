package io.hydrosphere.serving.manager.domain.servable

import cats.data.OptionT
import cats.effect.{Async, Timer}
import cats.implicits._
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.clouddriver.{CloudDriver, CloudInstance}
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.Servable.OkServable
import io.hydrosphere.serving.manager.infrastructure.grpc.PredictionClient
import io.hydrosphere.serving.tensorflow.api.prediction_service.StatusResponse.ServiceStatus.{NOT_SERVING, SERVING, UNKNOWN}

import scala.concurrent.duration._

trait ServableMonitor[F[_]] {
  def monitor(modelVersion: ModelVersion, suffix: String): F[OkServable]
}

object ServableMonitor {
  def default[F[_]](
    clientCtor: PredictionClient.Factory[F],
    cloudDriver: CloudDriver[F]
  )(
    implicit F: Async[F],
    timer: Timer[F]
  ) = {
    new ServableMonitor[F] {
      def pping(client: PredictionClient[F], sleepSeconds: Double, maxSleep: Double): F[String] = F.defer {
        for {
          response <- client.status()
          res <- response.status match {
            case SERVING => F.pure(response.message)
            case x if sleepSeconds > maxSleep => F.raiseError[String](DomainError.internalError(s"Servable max ping timeout ($maxSleep seconds) exceeded. Last state: $x ${response.message}"))
            case NOT_SERVING => timer.sleep(sleepSeconds.seconds) >> pping(client, Math.exp(sleepSeconds), maxSleep)
            case UNKNOWN => timer.sleep(sleepSeconds.seconds) >> pping(client, Math.exp(sleepSeconds), maxSleep)
          }
        } yield res
      }

      def ping(host: String, port: Int): F[Servable.Serving] = {
        clientCtor.make(host, port).use { client =>
          for {
            servingMessage <- pping(client, 5, 30.minutes.toSeconds)
          } yield Servable.Serving(servingMessage, host, port)
        }
      }

      def monitor(modelVersion: ModelVersion, suffix: String): F[OkServable] = {
        val name = Servable.fullName(modelVersion.model.name, modelVersion.modelVersion, suffix)
        for {
          c <- OptionT(cloudDriver.instance(name))
            .getOrElseF(F.raiseError(DomainError.internalError(s"Servable $name vanished unexpectedly")))
          x <- c.status match {
            case CloudInstance.Status.Stopped => F.raiseError[OkServable](DomainError.internalError(s"Servable $name stopped unexpectedly"))
            case CloudInstance.Status.Starting => timer.sleep(15.seconds) >> monitor(modelVersion, suffix)
            case CloudInstance.Status.Running(host, port) =>
              for {
                status <- ping(host, port)
              } yield Servable(modelVersion, suffix, status)
          }
        } yield x
      }
    }
  }
}