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
import io.hydrosphere.serving.manager.discovery.DiscoveryCtor
import io.hydrosphere.serving.manager.domain.application.Application.GenericApplication
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.Servable.GenericServable
import io.hydrosphere.serving.manager.infrastructure.db.ApplicationMigrationTool
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
    val cloudDriver = CloudDriver.fromConfig[F](config.cloudDriver, config.dockerRepository)
    val repositories = new Repositories[F](config)

    for {
      appDH <- DiscoveryCtor.topicBased[F, GenericApplication, Long]()
      (appPub, appSub) = appDH
      servDH <- DiscoveryCtor.topicBased[F, GenericServable, String]()
      (servPub, servSub) = servDH
      mdh <- DiscoveryCtor.topicBased[F, ModelVersion, Long]()
      (modelPub, modelSub) = mdh
      services = new Services[F] (
        appPub,
        servPub,
        modelPub,
        repositories,
        config,
        dockerClient,
        dockerConfig,
        cloudDriver,
        predictionCtor
      )

      n = ApplicationMigrationTool.default(
        repositories.applicationRepository,
        services.cloudDriverService,
        services.appDeployer,
        repositories.servableRepository
      )
      _ <- n.getAndRevive()
    } yield {
      val httpApi = new HttpApiServer(repositories, services, config)
      val grpcApi = GrpcApiServer(repositories, services, config, appSub, servSub)
      (httpApi, grpcApi)
    }
  }
}