package io.hydrosphere.serving.manager

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout
import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Resource, Timer}
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.api.grpc._
import io.hydrosphere.serving.manager.api.http.HttpServer
import io.hydrosphere.serving.manager.api.http.controller.application.ApplicationController
import io.hydrosphere.serving.manager.api.http.controller.events.SSEController
import io.hydrosphere.serving.manager.api.http.controller.host_selector.HostSelectorController
import io.hydrosphere.serving.manager.api.http.controller.model._
import io.hydrosphere.serving.manager.api.http.controller.servable.ServableController
import io.hydrosphere.serving.manager.api.http.controller._
import io.hydrosphere.serving.manager.config.ManagerConfiguration
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.domain.application.ApplicationRepository
import io.hydrosphere.serving.manager.domain.servable.ServableRepository
import io.hydrosphere.serving.manager.domain.model_build.BuildLogRepository
import io.hydrosphere.serving.manager.domain.model.ModelRepository
import io.hydrosphere.serving.manager.domain.model_version.ModelVersionRepository
import io.hydrosphere.serving.manager.domain.host_selector.HostSelectorRepository
import io.hydrosphere.serving.manager.domain.image.ImageRepository
import io.hydrosphere.serving.manager.domain.monitoring.MonitoringRepository
import io.hydrosphere.serving.manager.infrastructure.db.Database
import io.hydrosphere.serving.manager.infrastructure.db.repository._
import io.hydrosphere.serving.manager.infrastructure.docker.DockerdClient
import io.hydrosphere.serving.manager.infrastructure.grpc.{GrpcChannel, PredictionClient}
import io.hydrosphere.serving.manager.infrastructure.storage.StorageOps
import io.hydrosphere.serving.manager.util.UUIDGenerator
import io.hydrosphere.serving.manager.util.random.RNG

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

case class App[F[_]](
    config: ManagerConfiguration,
    core: Core[F],
    grpcServer: GrpcServer[F],
    httpServer: HttpServer[F],
    transactor: Transactor[F]
)

object App {
  def make[F[_]: ConcurrentEffect: ContextShift: Timer](
      config: ManagerConfiguration,
      dockerClient: DockerdClient[F]
  ): Resource[F, App[F]] = {
    implicit val system                  = ActorSystem("manager")
    implicit val materializer            = Materializer.createMaterializer(system)
    implicit val timeout                 = Timeout(5.minute)
    implicit val serviceExecutionContext = ExecutionContext.global
    implicit val grpcCtor                = GrpcChannel.plaintextFactory[F]
    implicit val predictionCtor          = PredictionClient.clientCtor[F](grpcCtor)
    implicit val storageOps              = StorageOps.default[F]
    implicit val uuidGen                 = UUIDGenerator.default[F]()
    implicit val dc                      = dockerClient
    for {
      implicit0(rng: RNG[F]) <- Resource.liftF(RNG.default[F])
      implicit0(cloudDriver: CloudDriver[F]) =
        CloudDriver
          .fromConfig[F](dockerClient, config.cloudDriver, config.dockerRepository)

      hk         <- Database.makeHikariDataSource[F](config.database)
      connectEc  <- ExecutionContexts.fixedThreadPool[F](32)
      transactEc <- Blocker[F]
      implicit0(tx: Transactor[F]) <- Resource.liftF(
        Database.makeTransactor[F](hk, connectEc, transactEc)
      )

      flyway <- Resource.liftF(Database.makeFlyway(tx))
      _      <- Resource.liftF(flyway.migrate())

      implicit0(hsRepo: HostSelectorRepository[F])           = DBHostSelectorRepository.make()
      implicit0(modelRepo: ModelRepository[F])               = DBModelRepository.make()
      implicit0(modelVersionRepo: ModelVersionRepository[F]) = DBModelVersionRepository.make()
      implicit0(servableRepo: ServableRepository[F])         = DBServableRepository.make()
      implicit0(appRepo: ApplicationRepository[F])           = DBApplicationRepository.make()
      implicit0(buildLogRepo: BuildLogRepository[F])         = DBBuildLogRepository.make()
      implicit0(monitoringRepo: MonitoringRepository[F])     = DBMonitoringRepository.make()
      implicit0(imageRepo: ImageRepository[F]) = ImageRepository.fromConfig(
        dockerClient,
        config.dockerRepository
      )

      core <- Resource.liftF(Core.make[F]())

      grpcService = new ManagerGrpcService[F](core.versionService, core.servableService)
      discoveryService = new GrpcServingDiscovery[F](
        core.appSub,
        core.servableSub,
        core.monitoringSub,
        core.appService,
        core.servableService,
        core.repos.monitoringRepository
      )
      grpc = GrpcServer.default(config, grpcService, discoveryService)

      externalModelController = new ExternalModelController[F](core.modelService)

      modelController = new ModelController[F](
        core.modelService,
        core.repos.modelRepo,
        core.versionService,
        core.buildLoggingService
      )
      appController      = new ApplicationController[F](core.appService)
      hsController       = new HostSelectorController[F](core.hostSelectorService)
      servableController = new ServableController[F](core.servableService, cloudDriver)
      sseController = new SSEController[F](
        core.appSub,
        core.modelSub,
        core.servableSub,
        core.monitoringSub
      )
      monitoringController = new MonitoringController[F](
        core.monitoringService,
        core.repos.monitoringRepository
      )

      apiClasses =
        modelController.getClass ::
          appController.getClass :: hsController.getClass ::
          servableController.getClass :: sseController.getClass ::
          monitoringController.getClass :: externalModelController.getClass :: Nil
      swaggerController = new SwaggerDocController(apiClasses.toSet, "2")

      http = HttpServer.akkaBased(
        config = config.application,
        swaggerRoutes = swaggerController.routes,
        modelRoutes = modelController.routes,
        applicationRoutes = appController.routes,
        hostSelectorRoutes = hsController.routes,
        servableRoutes = servableController.routes,
        sseRoutes = sseController.routes,
        monitoringRoutes = monitoringController.routes,
        externalModelRoutes = externalModelController.routes
      )
    } yield App(config, core, grpc, http, tx)
  }
}
