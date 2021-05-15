package io.hydrosphere.serving.manager

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.util.Timeout
import cats.effect._
import cats.syntax.all._
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.api.grpc.{
  GrpcServer,
  GrpcServingDiscovery,
  ManagerGrpcService
}
import io.hydrosphere.serving.manager.api.http.HttpServer
import io.hydrosphere.serving.manager.config.ManagerConfiguration
import io.hydrosphere.serving.manager.domain.application.migrations.ApplicationSignatureMigrationTool
import io.hydrosphere.serving.manager.domain.application.{ApplicationEvents, ApplicationMonitoring}
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.domain.deploy_config.{
  DeploymentConfigurationEvents,
  DeploymentConfigurationRepository
}
import io.hydrosphere.serving.manager.domain.image.ImageRepository
import io.hydrosphere.serving.manager.domain.model_version.ModelVersionEvents
import io.hydrosphere.serving.manager.domain.monitoring.MetricSpecEvents
import io.hydrosphere.serving.manager.domain.servable.{ServableEvents, ServableMonitoring}
import io.hydrosphere.serving.manager.infrastructure.db.{Database, FlywayClient}
import io.hydrosphere.serving.manager.infrastructure.db.repository._
import io.hydrosphere.serving.manager.infrastructure.docker.DockerdClient
import io.hydrosphere.serving.manager.infrastructure.grpc.GrpcChannel
import io.hydrosphere.serving.manager.infrastructure.storage.StorageOps
import io.hydrosphere.serving.manager.util.UUIDGenerator
import io.hydrosphere.serving.manager.util.random.RNG
import org.apache.logging.log4j.scala.Logging

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
    applicationMonitoring: ApplicationMonitoring[F],
    flywayClient: FlywayClient[F]
)

object App extends Logging {
  def actorSystem[F[_]](name: String)(implicit F: Async[F]): Resource[F, ActorSystem] =
    Resource.make(F.delay(ActorSystem(name)))(x => F.fromFuture(F.delay(x.terminate())).void)

  def make[F[_]](
      config: ManagerConfiguration,
      dockerClient: DockerdClient[F]
  )(implicit F: Async[F]): Resource[F, App[F]] = {
    implicit val timeout                 = Timeout(5.minute)
    implicit val serviceExecutionContext = ExecutionContext.global
    implicit val grpcCtor                = GrpcChannel.plaintextFactory[F]
    implicit val storageOps              = StorageOps.default[F]
    implicit val uuidGen                 = UUIDGenerator.default[F]()
    implicit val dc                      = dockerClient
    for {
      implicit0(actorSystem: ActorSystem) <- actorSystem("manager")
      implicit0(materializer: Materializer) = Materializer.createMaterializer(actorSystem)
      rngF <- Resource.eval(RNG.default[F])
      cloudDriver =
        CloudDriver.fromConfig[F](dockerClient, config.cloudDriver, config.dockerRepository)
      tx <- Database.makeTransactor[F](config.database)
      _ = logger.info("Created DB transactor")
      flyway <- Resource.eval(Database.makeFlyway(tx))

      appPubSub        <- Resource.eval(ApplicationEvents.makeTopic)
      modelPubSub      <- Resource.eval(ModelVersionEvents.makeTopic)
      servablePubSub   <- Resource.eval(ServableEvents.makeTopic)
      monitoringPubSub <- Resource.eval(MetricSpecEvents.makeTopic)
      depPubSub        <- Resource.eval(DeploymentConfigurationEvents.makeTopic)
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

        Resource.eval(Core.make[F](config))
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
      grpcService <-
        Resource.eval(ManagerGrpcService.make[F](core.versionService, core.servableService))
      discoveryService <- Resource.eval(
        GrpcServingDiscovery.make[F](
          appPubSub._2,
          servablePubSub._2,
          monitoringPubSub._2,
          core.appService,
          core.servableService,
          core.repos.monitoringRepository
        )
      )
      grpc = GrpcServer.default(config, grpcService, discoveryService)

      servableMonitoring = ServableMonitoring.make(
        cloudDriver,
        core.repos.servableRepo
      )
      applicationMonitoring = ApplicationMonitoring.make(
        servableSub = servablePubSub._2,
        appRepo = core.repos.appRepo,
        appPub = appPubSub._1
      )
      http <- HttpServer.akkaBased(
        config = config.application,
        core = core,
        cloudDriver = cloudDriver,
        appSub = appPubSub._2,
        servSub = servablePubSub._2,
        modelSub = modelPubSub._2,
        monitoringSub = monitoringPubSub._2,
        depSub = depPubSub._2
      )
    } yield App(
      config,
      core,
      grpc,
      http,
      tx,
      migrator,
      servableMonitoring,
      applicationMonitoring,
      flyway
    )
  }
}
