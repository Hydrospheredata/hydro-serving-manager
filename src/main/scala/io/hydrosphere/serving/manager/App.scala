package io.hydrosphere.serving.manager

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import cats.data.OptionT
import cats.effect.{ConcurrentEffect, ContextShift, Resource, Sync, Timer}
import cats.implicits._
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.api.grpc.{GrpcServer, GrpcServingDiscovery, ManagerGrpcService}
import io.hydrosphere.serving.manager.api.http.HttpServer
import io.hydrosphere.serving.manager.api.http.controller.application.ApplicationController
import io.hydrosphere.serving.manager.api.http.controller.events.SSEController
import io.hydrosphere.serving.manager.api.http.controller.model.{ExternalModelController, ModelController}
import io.hydrosphere.serving.manager.api.http.controller.servable.ServableController
import io.hydrosphere.serving.manager.api.http.controller.{DeploymentConfigController, HostSelectorController, MonitoringController, SwaggerDocController}
import io.hydrosphere.serving.manager.config.ManagerConfiguration
import io.hydrosphere.serving.manager.domain.application.migrations.ApplicationMigrationTool
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.domain.image.ImageRepository
import io.hydrosphere.serving.manager.infrastructure.db.Database
import io.hydrosphere.serving.manager.infrastructure.db.repository._
import io.hydrosphere.serving.manager.infrastructure.docker.DockerdClient
import io.hydrosphere.serving.manager.infrastructure.grpc.{GrpcChannel, PredictionClient}
import io.hydrosphere.serving.manager.infrastructure.storage.StorageOps
import io.hydrosphere.serving.manager.util.{FileUtils, UUIDGenerator}
import io.hydrosphere.serving.manager.util.random.RNG
import org.apache.commons.io.IOUtils

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.collection.JavaConverters._
import spray.json._


case class App[F[_]](
  config: ManagerConfiguration,
  core: Core[F],
  grpcServer: GrpcServer[F],
  httpServer: HttpServer[F],
  transactor: Transactor[F],
  migrationTool: ApplicationMigrationTool[F]
)

object App {
  def loadOpenApi[F[_]](implicit F: Sync[F]): F[JsValue] = {
    OptionT(F.delay(getClass.getClassLoader.getResourceAsStream("swagger.json")).map(Option.apply))
      .semiflatMap { stream =>
        F.delay(IOUtils.toString(stream, StandardCharsets.UTF_8))
          .map(_.parseJson)
      }
      .getOrElseF(F.raiseError(new IllegalStateException("Can't find OpenAPI spec (swagger.json) in resources")))
  }

  def make[F[_] : ConcurrentEffect : ContextShift : Timer](
    config: ManagerConfiguration,
    dockerClient: DockerdClient[F],
  ): Resource[F, App[F]] = {
    implicit val system = ActorSystem("manager")
    implicit val materializer = ActorMaterializer()
    implicit val timeout = Timeout(5.minute)
    implicit val serviceExecutionContext = ExecutionContext.global
    implicit val grpcCtor = GrpcChannel.plaintextFactory[F]
    implicit val predictionCtor = PredictionClient.clientCtor[F](grpcCtor)
    implicit val storageOps = StorageOps.default[F]
    implicit val uuidGen = UUIDGenerator.default[F]()
    implicit val dc = dockerClient
    for {
      openApi <- Resource.liftF(loadOpenApi[F])
      rngF <- Resource.liftF(RNG.default[F])
      cloudDriver = CloudDriver.fromConfig[F](dockerClient, config.cloudDriver, config.dockerRepository)
      hk <- Database.makeHikariDataSource[F](config.database)
      connectEc <- ExecutionContexts.fixedThreadPool[F](32)
      transactEc <- ExecutionContexts.cachedThreadPool[F]
      tx <- Resource.liftF(Database.makeTransactor[F](hk, connectEc, transactEc))
      flyway <- Resource.liftF(Database.makeFlyway(tx))
      _ <- Resource.liftF(flyway.migrate())
      core <- {
        implicit val rng = rngF
        implicit val cd = cloudDriver
        implicit val itx = tx
        implicit val hsRepo = new DBDeploymentConfigurationRepository()
        implicit val modelRepo = DBModelRepository.make()
        implicit val modelVersionRepo = DBModelVersionRepository.make()
        implicit val servableRepo = DBServableRepository.make()
        implicit val appRepo = DBApplicationRepository.make()
        implicit val buildLogRepo = DBBuildLogRepository.make()
        implicit val monitoringRepo = DBMonitoringRepository.make()
        implicit val imageRepo = ImageRepository.fromConfig(dockerClient, config.dockerRepository)

        Resource.liftF(Core.make[F]())
      }
      migrator = {
        implicit val tx1 = tx
        ApplicationMigrationTool
          .default(core.repos.appRepo, core.repos.versionRepo, cloudDriver, core.deployer, core.repos.servableRepo)
      }
      grpcService = new ManagerGrpcService[F](core.versionService, core.servableService)
      discoveryService = new GrpcServingDiscovery[F](core.appSub, core.servableSub, core.monitoringSub , core.appService, core.servableService, core.repos.monitoringRepository)
      grpc = GrpcServer.default(config, grpcService, discoveryService)

      externalModelController = new ExternalModelController[F](core.modelService)

      modelController = new ModelController[F](
        core.modelService,
        core.repos.modelRepo,
        core.versionService,
        core.buildLoggingService
      )
      appController = new ApplicationController[F](core.appService)
      hsController = new HostSelectorController[F]
      servableController = new ServableController[F](core.servableService, cloudDriver)
      sseController = new SSEController[F](core.appSub, core.modelSub, core.servableSub, core.monitoringSub)
      monitoringController = new MonitoringController[F](core.monitoringService, core.repos.monitoringRepository)
      depConfController = new DeploymentConfigController[F](core.deploymentConfigService)

//      apiClasses = modelController.getClass ::
//        appController.getClass :: hsController.getClass ::
//        servableController.getClass:: sseController.getClass ::
//        monitoringController.getClass :: externalModelController.getClass ::
//        depConfController.getClass :: Nil
//      swaggerController = new SwaggerDocController(apiClasses.toSet, "2")
      swaggerController = new SwaggerDocController(openApi)

      http = HttpServer.akkaBased(
        config = config.application,
        swaggerRoutes = swaggerController.routes,
        modelRoutes = modelController.routes,
        applicationRoutes = appController.routes,
        hostSelectorRoutes = hsController.routes,
        servableRoutes = servableController.routes,
        sseRoutes = sseController.routes,
        monitoringRoutes = monitoringController.routes,
        externalModelRoutes = externalModelController.routes,
        deploymentConfRoutes = depConfController.routes
      )
    } yield App(config, core, grpc, http, tx, migrator)
  }
}