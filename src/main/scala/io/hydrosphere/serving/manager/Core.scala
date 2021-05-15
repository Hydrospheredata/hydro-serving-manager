package io.hydrosphere.serving.manager

import cats.effect._
import cats.effect.std.Dispatcher
import cats.implicits._
import io.hydrosphere.serving.manager.config.ManagerConfiguration
import io.hydrosphere.serving.manager.domain.application.{
  ApplicationDeployer,
  ApplicationRepository,
  ApplicationService
}
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.domain.deploy_config.{
  DeploymentConfigurationRepository,
  DeploymentConfigurationService
}
import io.hydrosphere.serving.manager.domain.image.ImageRepository
import io.hydrosphere.serving.manager.domain.model.{ModelRepository, ModelService}
import io.hydrosphere.serving.manager.domain.model_build.{
  BuildLogRepository,
  BuildLoggingService,
  ModelVersionBuilder
}
import io.hydrosphere.serving.manager.domain.model_version.{
  ModelVersionRepository,
  ModelVersionService
}
import io.hydrosphere.serving.manager.domain.monitoring.{Monitoring, MonitoringRepository}
import io.hydrosphere.serving.manager.domain.servable._
import io.hydrosphere.serving.manager.infrastructure.docker.DockerdClient
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.ModelFetcher
import io.hydrosphere.serving.manager.infrastructure.storage.{ModelUnpacker, StorageOps}
import io.hydrosphere.serving.manager.util.UUIDGenerator
import io.hydrosphere.serving.manager.util.random.{NameGenerator, RNG}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

case class Repositories[F[_]](
    appRepo: ApplicationRepository[F],
    hsRepo: DeploymentConfigurationRepository[F],
    modelRepo: ModelRepository[F],
    versionRepo: ModelVersionRepository[F],
    servableRepo: ServableRepository[F],
    buildLogRepo: BuildLogRepository[F],
    monitoringRepository: MonitoringRepository[F],
    depConfRepository: DeploymentConfigurationRepository[F]
)

final case class Core[F[_]](
    deployer: ApplicationDeployer[F],
    repos: Repositories[F],
    buildLoggingService: BuildLoggingService[F],
    deploymentConfigService: DeploymentConfigurationService[F],
    modelService: ModelService[F],
    versionService: ModelVersionService[F],
    appService: ApplicationService[F],
    servableService: ServableService[F],
    monitoringService: Monitoring[F]
)

object Core {
  def make[F[_]](
      config: ManagerConfiguration
  )(implicit
      F: Async[F],
      ec: ExecutionContext,
      timer: Clock[F],
      rng: RNG[F],
      uuid: UUIDGenerator[F],
      storageOps: StorageOps[F],
      dockerClient: DockerdClient[F],
      cloudDriver: CloudDriver[F],
      imageRepository: ImageRepository[F],
      modelRepo: ModelRepository[F],
      modelVersionRepo: ModelVersionRepository[F],
      deploymentConfigRepo: DeploymentConfigurationRepository[F],
      servableRepo: ServableRepository[F],
      appRepo: ApplicationRepository[F],
      buildLogsRepo: BuildLogRepository[F],
      monitoringRepo: MonitoringRepository[F]
  ): F[Core[F]] =
    for {
      buildLoggingService <- BuildLoggingService.make[F]()
      core <- {
        implicit val bl: BuildLoggingService[F]      = buildLoggingService
        implicit val nameGen: NameGenerator[F]       = NameGenerator.haiku[F]()
        implicit val modelUnpacker: ModelUnpacker[F] = ModelUnpacker.default[F]
        implicit val modelFetcher: ModelFetcher[F]   = ModelFetcher.default[F]
        implicit val deploymentConfigService: DeploymentConfigurationService[F] =
          DeploymentConfigurationService[F](deploymentConfigRepo)
        implicit val versionService: ModelVersionService[F] = ModelVersionService[F]()
        implicit val servableService: ServableService[F] =
          ServableService[F](config.defaultDeploymentConfiguration)
        implicit val monitoringService: Monitoring[F]       = Monitoring[F]()
        implicit val versionBuilder: ModelVersionBuilder[F] = ModelVersionBuilder()
        for {
          gc <- ServableGC.empty[F](1.hour)
        } yield {
          implicit val servableGC: ServableGC[F]           = gc
          implicit val appDeployer: ApplicationDeployer[F] = ApplicationDeployer.default()
          implicit val appService: ApplicationService[F]   = ApplicationService[F]()
          implicit val modelService: ModelService[F]       = ModelService[F]()

          val repos = Repositories(
            appRepo,
            deploymentConfigRepo,
            modelRepo,
            modelVersionRepo,
            servableRepo,
            buildLogsRepo,
            monitoringRepo,
            deploymentConfigRepo
          )
          Core(
            deployer = appDeployer,
            repos = repos,
            buildLoggingService = buildLoggingService,
            deploymentConfigService = deploymentConfigService,
            modelService = modelService,
            versionService = versionService,
            appService = appService,
            servableService = servableService,
            monitoringService = monitoringService
          )
        }
      }
    } yield core
}
