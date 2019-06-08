package io.hydrosphere.serving.manager
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import cats.effect._
import cats.implicits._
import com.spotify.docker.client.DefaultDockerClient
import io.grpc.Server
import io.hydrosphere.serving.manager.api.grpc.GrpcApiServer
import io.hydrosphere.serving.manager.api.http.HttpApiServer
import io.hydrosphere.serving.manager.config.{DockerClientConfig, ManagerConfiguration}
import io.hydrosphere.serving.manager.discovery.application.ApplicationDiscoveryHub
import io.hydrosphere.serving.manager.domain.application.Application
import io.hydrosphere.serving.manager.domain.application.Application.ReadyApp
import io.hydrosphere.serving.manager.domain.application.ApplicationService.Internals
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.infrastructure.grpc.PredictionClient
import io.hydrosphere.serving.manager.util.random.RNG
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.ExecutionContext

object Core extends Logging {
  def app[F[_]](
    config: ManagerConfiguration,
    repos: Repositories[F],
    dockerClient: DefaultDockerClient,
    dockerConfig: DockerClientConfig,
    predictionCtor: PredictionClient.Factory[F]
  )(
    implicit F: ConcurrentEffect[F],
    ec: ExecutionContext,
    as: ActorSystem,
    mat: ActorMaterializer,
    timeout: Timeout,
    timer: Timer[F],
    rng: RNG[F]
  ): F[(HttpApiServer[F], Server)] = {
    val cloudDriver  = CloudDriver.fromConfig[F](config.cloudDriver, config.dockerRepository)
    val repositories = new Repositories[F](config)
    val discoveryHubIO = for {
      observed <- ApplicationDiscoveryHub.observed[F]
      apps     <- repositories.applicationRepository.all()
      needToDiscover = apps.flatMap { app =>
        app.status match {
          case _: Application.Ready =>
            Internals.toServingApp(app.asInstanceOf[ReadyApp]) :: Nil
          case _ => Nil
        }
      }
      _ <- needToDiscover.traverse(observed.added)
    } yield observed

    for {
      dh <- discoveryHubIO
    } yield {
      val services = new Services[F](dh, repositories, config, dockerClient, dockerConfig, cloudDriver, predictionCtor)

      val httpApi = new HttpApiServer(repositories, services, config)
      val grpcApi = GrpcApiServer(repositories, services, config, dh)

      (httpApi, grpcApi)
    }
  }
}
