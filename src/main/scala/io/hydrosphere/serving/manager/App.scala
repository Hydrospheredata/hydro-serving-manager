package io.hydrosphere.serving.manager

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import cats.implicits._
import cats.effect.implicits._
import cats.effect.{ConcurrentEffect, ContextShift, Effect, IO, Resource, Sync, Timer}
import com.spotify.docker.client.DockerClient
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.api.ManagerServiceGrpc
import io.hydrosphere.serving.manager.api.grpc.{GrpcServer, GrpcServingDiscovery, ManagerGrpcService}
import io.hydrosphere.serving.manager.api.http.HttpServer
import io.hydrosphere.serving.manager.api.http.controller.SwaggerDocController
import io.hydrosphere.serving.manager.api.http.controller.application.ApplicationController
import io.hydrosphere.serving.manager.api.http.controller.events.SSEController
import io.hydrosphere.serving.manager.api.http.controller.host_selector.HostSelectorController
import io.hydrosphere.serving.manager.api.http.controller.model.ModelController
import io.hydrosphere.serving.manager.api.http.controller.servable.ServableController
import io.hydrosphere.serving.manager.config.{DockerClientConfig, ManagerConfiguration}
import io.hydrosphere.serving.manager.domain.application.ApplicationRepository
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.domain.host_selector.HostSelectorRepository
import io.hydrosphere.serving.manager.domain.image.ImageRepository
import io.hydrosphere.serving.manager.domain.model.ModelRepository
import io.hydrosphere.serving.manager.domain.model_build.BuildLogRepository
import io.hydrosphere.serving.manager.domain.model_version.ModelVersionRepository
import io.hydrosphere.serving.manager.domain.servable.ServableRepository
import io.hydrosphere.serving.manager.infrastructure.db.Database
import io.hydrosphere.serving.manager.infrastructure.db.repository.{DBApplicationRepository, DBBuildLogRepository, DBHostSelectorRepository, DBModelRepository, DBModelVersionRepository, DBServableRepository}
import io.hydrosphere.serving.manager.infrastructure.grpc.{GrpcChannel, PredictionClient}
import io.hydrosphere.serving.manager.infrastructure.image.DockerImageBuilder
import io.hydrosphere.serving.manager.infrastructure.storage.StorageOps
import io.hydrosphere.serving.manager.util.random.RNG

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import io.hydrosphere.serving.manager.util.docker.DockerProgress


case class App[F[_]](
  config: ManagerConfiguration,
  core: Core[F],
  grpcServer: GrpcServer[F],
  httpServer: HttpServer[F],
  transactor: Transactor[F],
)

object App {
  def make[F[_] : ConcurrentEffect : ContextShift : Timer](
    config: ManagerConfiguration,
    dockerClient: DockerClient,
    dockerClientConfig: DockerClientConfig
  ): Resource[F, App[F]] = {
    implicit val system = ActorSystem("manager")
    implicit val materializer = ActorMaterializer()
    implicit val timeout = Timeout(5.minute)
    implicit val serviceExecutionContext = ExecutionContext.global
    implicit val grpcCtor = GrpcChannel.plaintextFactory[F]
    implicit val predictionCtor = PredictionClient.clientCtor[F](grpcCtor)
    implicit val storageOps = StorageOps.default[F]

    for {
      rngF <- Resource.liftF(RNG.default[F])
      cloudDriver = CloudDriver.fromConfig[F](config.cloudDriver, config.dockerRepository)
      hk <- Database.makeHikariDataSource[F](config.database)
      tx <- Resource.liftF(Database.makeTransactor[F](hk, ExecutionContext.global, ExecutionContext.global))
      flyway <- Resource.liftF(Database.makeFlyway(tx))
      _ <- Resource.liftF(flyway.migrate())
      dockerLogger = DockerProgress.makeLogger(println)
      core <- {
        implicit val rng = rngF
        implicit val cd = cloudDriver
        implicit val itx = tx
        implicit val hsRepo = DBHostSelectorRepository.make()
        implicit val modelRepo = DBModelRepository.make()
        implicit val modelVersionRepo = DBModelVersionRepository.make()
        implicit val servableRepo = DBServableRepository.make()
        implicit val appRepo = DBApplicationRepository.make()
        implicit val buildLogRepo = DBBuildLogRepository.make()
        implicit val imageRepo = ImageRepository.fromConfig(dockerClient, dockerLogger, config.dockerRepository)
        implicit val imageBuilder = new DockerImageBuilder(dockerClient, dockerClientConfig)

        Resource.liftF(Core.make[F]())
      }
      grpcService = new ManagerGrpcService[F](core.versionService, core.servableService)
      discoveryService = new GrpcServingDiscovery[F](core.appSub, core.servableSub, core.appService, core.servableService)
      grpc = GrpcServer.default(config, grpcService, discoveryService)

      modelController = new ModelController[F](
        core.modelService,
        core.repos.modelRepo,
        core.versionService,
        core.buildLoggingService
      )
      appController = new ApplicationController[F](core.appService)
      hsController = new HostSelectorController[F](core.hostSelectorService)
      servableController = new ServableController[F](core.servableService, cloudDriver)
      sseController = new SSEController[F](core.appSub, core.modelSub, core.servableSub)

      apiClasses = modelController.getClass ::
        appController.getClass :: hsController.getClass ::
        servableController.getClass:: sseController.getClass :: Nil
      swaggerController = new SwaggerDocController(apiClasses.toSet, "2")

      http = HttpServer.akkaBased(
        config = config.application,
        swaggerRoutes = swaggerController.routes,
        modelRoutes = modelController.routes,
        applicationRoutes = appController.routes,
        hostSelectorRoutes = hsController.routes,
        servableRoutes = servableController.routes,
        sseRoutes = sseController.routes
      )
    } yield App(config, core, grpc, http, tx)
  }
}