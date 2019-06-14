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
import io.hydrosphere.serving.manager.discovery.servable.ServableDiscoveryHub
import io.hydrosphere.serving.manager.domain.application.Application.ReadyApp
import io.hydrosphere.serving.manager.domain.application.ApplicationService.Internals
import io.hydrosphere.serving.manager.domain.application.Application
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.hydrosphere.serving.manager.infrastructure.db.ApplicationMigrationTool
import io.hydrosphere.serving.manager.infrastructure.grpc.PredictionClient
import io.hydrosphere.serving.manager.util.grpc.Converters
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
    val cloudDriver = CloudDriver.fromConfig[F](config.cloudDriver, config.dockerRepository)
    val repositories = new Repositories[F](config)

    for {
      dh <- ApplicationDiscoveryHub.observed[F]
      sdh <- ServableDiscoveryHub.observed[F]
      services = new Services[F](dh, sdh, repositories, config, dockerClient, dockerConfig, cloudDriver, predictionCtor)
      n = ApplicationMigrationTool.default(
        repositories.applicationRepository,
        services.cloudDriverService,
        services.appDeployer,
        repositories.servableRepository
      )
      _ <- n.getAndRevive()
      servables <- repositories.servableRepository.all()
      servablesToDiscover = servables.flatMap { s =>
        s.status match {
          case Servable.Serving(_, _, _) =>
            Converters.fromServable(s) :: Nil
          case _ => Nil
        }
      }
      _ <- sdh.added(servablesToDiscover)
      apps <- repositories.applicationRepository.all()
      appsToDiscover = apps.flatMap { app =>
        app.status match {
          case _: Application.Ready =>
            Internals.toServingApp(app.asInstanceOf[ReadyApp]) :: Nil
          case _ => Nil
        }
      }
      _ <- appsToDiscover.traverse(dh.added)
    } yield {
      val httpApi = new HttpApiServer(repositories, services, config)
      val grpcApi = GrpcApiServer(repositories, services, config, dh, sdh)
      (httpApi, grpcApi)
    }
  }
}