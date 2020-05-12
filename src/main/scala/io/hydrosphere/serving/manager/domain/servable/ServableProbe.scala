package io.hydrosphere.serving.manager.domain.servable

import cats.data.OptionT
import cats.effect.{Concurrent, Timer}
import cats.implicits._
import io.hydrosphere.serving.manager.domain.clouddriver.{CloudDriver, CloudInstance}
import io.hydrosphere.serving.manager.infrastructure.grpc.PredictionClient
import io.hydrosphere.serving.tensorflow.api.prediction_service.StatusResponse.ServiceStatus._

sealed trait ProbeResponse extends Product with Serializable {
  def getStatus: Servable.Status
  def getHost: Option[String]
  def getPort: Option[Int]
  def message: String
}

object ProbeResponse {

  case class PingError(
      status: Servable.Status,
      host: String,
      port: Int,
      message: String
  ) extends ProbeResponse {
    val getStatus: Servable.Status = status

    val getHost: Option[String] = host.some

    val getPort: Option[Int] = port.some
  }

  case class InstanceNotFound(
      message: String
  ) extends ProbeResponse {
    val getStatus: Servable.Status = Servable.Status.NotServing

    val getHost: Option[String] = None

    val getPort: Option[Int] = None
  }

  case class Ok(
      host: String,
      port: Int,
      message: String
  ) extends ProbeResponse {
    val getStatus: Servable.Status = Servable.Status.Serving

    val getHost: Option[String] = host.some

    val getPort: Option[Int] = port.some
  }
}

trait ServableProbe[F[_]] {
  def probe(servable: Servable): F[ProbeResponse]
}

object ServableProbe {
  def default[F[_]](clientCtor: PredictionClient.Factory[F], cloudDriver: CloudDriver[F])(implicit
      F: Concurrent[F],
      timer: Timer[F]
  ): ServableProbe[F] =
    new ServableProbe[F] {
      def ping(host: String, port: Int, client: PredictionClient[F]): F[ProbeResponse] =
        for {
          resp <- client.status().attempt
        } yield resp match {
          case Left(error) =>
            ProbeResponse.PingError(
              host = host,
              port = port,
              message = s"Ping error: ${error.getMessage}",
              status = Servable.Status.NotServing
            )
          case Right(response) =>
            response.status match {
              case SERVING =>
                ProbeResponse.Ok(
                  host = host,
                  port = port,
                  message = response.message
                )
              case NOT_SERVING =>
                ProbeResponse.PingError(
                  host = host,
                  port = port,
                  message = response.message,
                  status = Servable.Status.NotServing
                )
              case UNKNOWN =>
                ProbeResponse.PingError(
                  host = host,
                  port = port,
                  message = response.message,
                  status = Servable.Status.Starting
                )
              case x =>
                ProbeResponse.PingError(
                  host = host,
                  port = port,
                  message = s"Unknown response message: $x",
                  status = Servable.Status.NotServing
                )
            }
        }

      def probe(name: String): F[ProbeResponse] =
        OptionT(cloudDriver.instance(name))
          .semiflatMap { x =>
            x.status match {
              case CloudInstance.Status.Running(host, port) =>
                clientCtor.make(host, port).use(client => ping(host, port, client))

              case CloudInstance.Status.Stopped =>
                F.pure(ProbeResponse.InstanceNotFound("Underlying instance stopped"))
                  .widen[ProbeResponse]

              case CloudInstance.Status.Starting =>
                F.pure(ProbeResponse.InstanceNotFound("Underlying instance starting"))
                  .widen[ProbeResponse]
            }
          }
          .getOrElseF(
            F.pure(ProbeResponse.InstanceNotFound(s"Servable $name doesn't exist"))
              .widen[ProbeResponse]
          )

      override def probe(servable: Servable): F[ProbeResponse] = probe(servable.fullName)
    }
}
