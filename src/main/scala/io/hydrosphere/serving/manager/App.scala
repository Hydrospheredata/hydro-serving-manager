package io.hydrosphere.serving.manager

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import cats.effect._
import cats.implicits._
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor
import org.apache.commons.io.IOUtils

import io.hydrosphere.serving.manager.api.grpc.{
  GrpcServer,
  GrpcServingDiscovery,
  ManagerGrpcService
}
import io.hydrosphere.serving.manager.api.http.HttpServer
import io.hydrosphere.serving.manager.api.http.controller.application.ApplicationController
import io.hydrosphere.serving.manager.api.http.controller.events.SSEController
import io.hydrosphere.serving.manager.api.http.controller.model.{
  ExternalModelController,
  ModelController
}
import io.hydrosphere.serving.manager.api.http.controller.servable.ServableController
import io.hydrosphere.serving.manager.api.http.controller.{
  DeploymentConfigController,
  HostSelectorController,
  MonitoringController
}
import io.hydrosphere.serving.manager.config.ManagerConfiguration
import io.hydrosphere.serving.manager.domain.application.{ApplicationEvents, ApplicationMonitoring}
import io.hydrosphere.serving.manager.domain.application.migrations.ApplicationSignatureMigrationTool
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.domain.deploy_config.{
  DeploymentConfigurationEvents,
  DeploymentConfigurationRepository
}
import io.hydrosphere.serving.manager.domain.image.ImageRepository
import io.hydrosphere.serving.manager.domain.model_version.ModelVersionEvents
import io.hydrosphere.serving.manager.domain.monitoring.MetricSpecEvents
import io.hydrosphere.serving.manager.domain.servable.{
  ServableEvents,
  ServableMonitoring,
  ServableRepository
}
import io.hydrosphere.serving.manager.infrastructure.db.Database
import io.hydrosphere.serving.manager.infrastructure.db.repository._
import io.hydrosphere.serving.manager.infrastructure.docker.DockerdClient
import io.hydrosphere.serving.manager.infrastructure.grpc.GrpcChannel
import io.hydrosphere.serving.manager.infrastructure.storage.StorageOps
import io.hydrosphere.serving.manager.util.random.RNG
import io.hydrosphere.serving.manager.util.UUIDGenerator

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

case class App[F[_]](
    config: ManagerConfiguration,
    core: Core[F],
    grpcServer: GrpcServer[F],
    httpServer: HttpServer[F],
    transactor: Transactor[F],
    migrationTool: ApplicationSignatureMigrationTool[F],
    servableMonitoring: ServableMonitoring[F],
    applicationMonitoring: ApplicationMonitoring[F]
)

object App {
  def make[F[_]: ConcurrentEffect: ContextShift: Timer](
      config: ManagerConfiguration,
      dockerClient: DockerdClient[F]
  ): Resource[F, App[F]] = {
    implicit val system                  = ActorSystem("manager")
    implicit val materializer            = ActorMaterializer()
    implicit val timeout                 = Timeout(5.minute)
    implicit val serviceExecutionContext = ExecutionContext.global
    implicit val grpcCtor                = GrpcChannel.plaintextFactory[F]
    implicit val storageOps              = StorageOps.default[F]
    implicit val uuidGen                 = UUIDGenerator.default[F]()
    implicit val dc                      = dockerClient
    for {
      rngF <- Resource.liftF(RNG.default[F])
      cloudDriver =
        CloudDriver.fromConfig[F](dockerClient, config.cloudDriver, config.dockerRepository)
      hk         <- Database.makeHikariDataSource[F](config.database)
      blocker    <- Blocker[F]
      transactEc <- ExecutionContexts.cachedThreadPool[F]
      tx         <- Resource.liftF(Database.makeTransactor[F](hk, transactEc, blocker))
      flyway     <- Resource.liftF(Database.makeFlyway(tx))
      _          <- Resource.liftF(flyway.migrate())

      appPubSub        <- Resource.liftF(ApplicationEvents.makeTopic)
      modelPubSub      <- Resource.liftF(ModelVersionEvents.makeTopic)
      servablePubSub   <- Resource.liftF(ServableEvents.makeTopic)
      monitoringPubSub <- Resource.liftF(MetricSpecEvents.makeTopic)
      depPubSub        <- Resource.liftF(DeploymentConfigurationEvents.makeTopic)
      core <- {
        implicit val rng                        = rngF
        implicit val cd                         = cloudDriver
        implicit val itx                        = tx
        implicit val (appPub, appSub)           = appPubSub
        implicit val (modelPub, modelSub)       = modelPubSub
        implicit val (servablePub, servableSub) = servablePubSub
        implicit val (metricPub, metricSub)     = monitoringPubSub
        implicit val (depPub, depSUb)           = depPubSub
        implicit val hsRepo =
          DeploymentConfigurationRepository.make(config.defaultDeploymentConfiguration)
        implicit val modelRepo        = DBModelRepository.make()
        implicit val modelVersionRepo = DBModelVersionRepository.make()
        implicit val servableRepo     = DBServableRepository.make(config.defaultDeploymentConfiguration)
        implicit val appRepo          = DBApplicationRepository.make(config.defaultDeploymentConfiguration)
        implicit val buildLogRepo     = DBBuildLogRepository.make()
        implicit val monitoringRepo =
          DBMonitoringRepository.make(config.defaultDeploymentConfiguration)
        implicit val imageRepo = ImageRepository.fromConfig(dockerClient, config.dockerRepository)

        Resource.liftF(Core.make[F](config))
      }
      migrator = {
        implicit val tx1 = tx
        ApplicationSignatureMigrationTool.default(
          core.repos.appRepo,
          core.repos.versionRepo,
          core.repos.servableRepo,
          core.repos.depConfRepository
        )
      }
      grpcService = new ManagerGrpcService[F](core.versionService, core.servableService)
      discoveryService = new GrpcServingDiscovery[F](
        appPubSub._2,
        servablePubSub._2,
        monitoringPubSub._2,
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
      hsController       = new HostSelectorController[F]
      servableController = new ServableController[F](core.servableService, cloudDriver)
      sseController = new SSEController[F](
        appPubSub._2,
        modelPubSub._2,
        servablePubSub._2,
        monitoringPubSub._2,
        depPubSub._2
      )
      monitoringController =
        new MonitoringController[F](core.monitoringService, core.repos.monitoringRepository)
      depConfController = new DeploymentConfigController[F](core.deploymentConfigService)
      servableMonitoring = ServableMonitoring.make(
        cloudDriver,
        core.repos.servableRepo
      )
      applicationMonitoring = ApplicationMonitoring.make(
        servablePub = servablePubSub._2,
        appRepo = core.repos.appRepo
      )
      http = HttpServer.akkaBased(
        config = config.application,
        modelRoutes = modelController.routes,
        applicationRoutes = appController.routes,
        hostSelectorRoutes = hsController.routes,
        servableRoutes = servableController.routes,
        sseRoutes = sseController.routes,
        monitoringRoutes = monitoringController.routes,
        externalModelRoutes = externalModelController.routes,
        deploymentConfRoutes = depConfController.routes
      )
    } yield App(config, core, grpc, http, tx, migrator, servableMonitoring, applicationMonitoring)
  }
}
