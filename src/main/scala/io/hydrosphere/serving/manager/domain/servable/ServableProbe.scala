package io.hydrosphere.serving.manager.domain.servable

import cats.data.OptionT
import cats.effect.{Concurrent, Timer}
import cats.implicits._
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.clouddriver.{CloudDriver, CloudInstance}
import io.hydrosphere.serving.manager.domain.servable.Servable.GenericServable
import io.hydrosphere.serving.manager.infrastructure.grpc.PredictionClient
import io.hydrosphere.serving.tensorflow.api.prediction_service.StatusResponse.ServiceStatus._

trait ServableProbe[F[_]] {
  def probe(servable: GenericServable): F[Servable.Status]
}

object ServableProbe {
  def default[F[_]](
    clientCtor: PredictionClient.Factory[F],
    cloudDriver: CloudDriver[F],
  )(implicit F: Concurrent[F], timer: Timer[F]): ServableProbe[F] = {
    new ServableProbe[F] {
      def ping(name: String): F[Servable.Status] = {
        for {
          c <- OptionT(cloudDriver.instance(name))
            .getOrElseF(F.raiseError(DomainError.internalError(s"Servable $name doesn't exist")))
          x <- c.status match {
            case CloudInstance.Status.Running(host, port) =>
              clientCtor.make(host, port).use { client =>
                for {
                  resp <- client.status().attempt
                } yield {
                  resp match {
                    case Left(error) => Servable.NotAvailable(s"Ping error: ${error.getMessage}", host.some, port.some)
                    case Right(response) => response.status match {
                      case SERVING => Servable.Serving(response.message, host, port)
                      case NOT_SERVING => Servable.NotServing(response.message, host.some, port.some)
                      case UNKNOWN => Servable.Starting(response.message, host.some, port.some)
                      case x => Servable.NotServing(s"Unknown response message: $x", host.some, port.some)
                    }
                  }
                }
              }
            case CloudInstance.Status.Stopped =>
              F.pure(Servable.NotAvailable("Underlying instance stopped", None, None).asInstanceOf[Servable.Status])
            case CloudInstance.Status.Starting =>
              F.pure(Servable.Starting("Underlying instance starting", None, None).asInstanceOf[Servable.Status])
          }
        } yield x
      }

      override def probe(servable: GenericServable): F[Servable.Status] = ping(servable.fullName)
    }
  }
}