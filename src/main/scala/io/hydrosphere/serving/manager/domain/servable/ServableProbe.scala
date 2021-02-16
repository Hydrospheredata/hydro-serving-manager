package io.hydrosphere.serving.manager.domain.servable

//import cats.data.OptionT
import cats.effect.{Concurrent, Timer}
import cats.implicits._
//import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.clouddriver.{CloudDriver, CloudInstance}
//import io.hydrosphere.serving.manager.domain.servable.Servable
//import io.hydrosphere.serving.manager.infrastructure.grpc.PredictionClient
//import io.hydrosphere.serving.tensorflow.api.prediction_service.StatusResponse.ServiceStatus._

import io.hydrosphere.serving.proto.runtime.api.PredictResponse

trait ServableProbe[F[_]] {
  def probe(servable: Servable): F[Servable.Status]
}

object ServableProbe {
  def default[F[_]]()(implicit
      F: Concurrent[F],
      timer: Timer[F],
//      clientCtor: PredictionClient.Factory[F],
      cloudDriver: CloudDriver[F]
  ): ServableProbe[F] =
    new ServableProbe[F] {
      def ping(name: String): F[Servable.Status] =
        for {
          x <- F.pure(Servable.Status.NotServing)
//          c <- OptionT(cloudDriver.instance(name))
//            .getOrElseF(F.raiseError(DomainError.internalError(s"Servable $name doesn't exist")))
//          x <- c.status match {
//            case CloudInstance.Status.Running(host, port) =>
//              clientCtor.make(host, port).use { client =>
//                for {
//                  resp <- client.status().attempt
//                } yield resp match {
//                  case Left(error) => Servable.Status.NotAvailable
//                  case Right(response) =>
//                    response.status match {
//                      case SERVING     => Servable.Status.Serving
//                      case NOT_SERVING => Servable.Status.NotServing
//                      case UNKNOWN     => Servable.Status.Starting
//                      case x           => Servable.Status.NotServing
//                    }
//                }
//              }
//            case CloudInstance.Status.Stopped =>
//              F.pure(Servable.Status.NotAvailable)
//            case CloudInstance.Status.Starting =>
//              F.pure(Servable.Status.Starting)
//          }
        } yield x

      override def probe(servable: Servable): F[Servable.Status] = ping(servable.fullName)
    }
}
