package io.hydrosphere.serving.manager

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import cats.effect.{ConcurrentEffect, Timer}
import com.spotify.docker.client._
import io.hydrosphere.serving.manager.config.{DockerClientConfig, ManagerConfiguration}
import io.hydrosphere.serving.manager.discovery.application.ApplicationDiscoveryHub
import io.hydrosphere.serving.manager.domain.application.ApplicationService
import io.hydrosphere.serving.manager.domain.application.graph.VersionGraphComposer
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.domain.host_selector.HostSelectorService
import io.hydrosphere.serving.manager.domain.image.ImageRepository
import io.hydrosphere.serving.manager.domain.model.ModelService
import io.hydrosphere.serving.manager.domain.model_build.ModelVersionBuilder
import io.hydrosphere.serving.manager.domain.model_version.ModelVersionService
import io.hydrosphere.serving.manager.domain.servable.{ServableMonitor, ServableService}
import io.hydrosphere.serving.manager.infrastructure.grpc.PredictionClient
import io.hydrosphere.serving.manager.infrastructure.image.DockerImageBuilder
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.ModelFetcher
import io.hydrosphere.serving.manager.infrastructure.storage.{LocalStorageOps, ModelUnpacker, StorageOps}
import io.hydrosphere.serving.manager.util.docker.InfoProgressHandler
import io.hydrosphere.serving.manager.util.random.{NameGenerator, RNG}
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.ExecutionContext

class Services[F[_]: ConcurrentEffect](
  val discoveryHub: ApplicationDiscoveryHub[F],
  val managerRepositories: Repositories[F],
  val managerConfiguration: ManagerConfiguration,
  val dockerClient: DockerClient,
  val dockerClientConfig: DockerClientConfig,
  val cloudDriverService: CloudDriver[F],
  val predictionCtor: PredictionClient.Factory[F]
)(
  implicit val ex: ExecutionContext,
  implicit val system: ActorSystem,
  implicit val materializer: ActorMaterializer,
  implicit val timeout: Timeout,
  implicit val timer: Timer[F],
  implicit val rng: RNG[F]
) extends Logging {

  val progressHandler: ProgressHandler = InfoProgressHandler

  val nameGen: NameGenerator[F] = NameGenerator.haiku()
  
  val storageOps: LocalStorageOps[F] = StorageOps.default

  val modelStorage: ModelUnpacker[F] = ModelUnpacker[F](storageOps)

  val modelFetcher: ModelFetcher[F] = ModelFetcher.default[F](storageOps)

  val imageBuilder = new DockerImageBuilder(
    dockerClient = dockerClient,
    dockerClientConfig = dockerClientConfig,
    modelStorage = modelStorage,
    progressHandler = progressHandler
  )

  val imageRepository: ImageRepository[F] = ImageRepository.fromConfig(dockerClient, progressHandler, managerConfiguration.dockerRepository)

  val hostSelectorService: HostSelectorService[F] = HostSelectorService[F](managerRepositories.hostSelectorRepository)

  val versionService: ModelVersionService[F] = ModelVersionService[F](
    modelVersionRepository = managerRepositories.modelVersionRepository,
    applicationRepo = managerRepositories.applicationRepository,
  )

  val versionBuilder = ModelVersionBuilder(
    imageBuilder = imageBuilder,
    modelVersionRepository = managerRepositories.modelVersionRepository,
    imageRepository = imageRepository,
    modelVersionService = versionService,
    storageOps = storageOps
  )

  logger.info(s"Using ${cloudDriverService.getClass} cloud driver")

  val servableMonitor: ServableMonitor[F] = ServableMonitor.default[F](predictionCtor, cloudDriverService)

  val servableService: ServableService[F] = ServableService[F](
    cloudDriverService,
    managerRepositories.servableRepository,
    nameGen,
    predictionCtor,
    servableMonitor
  )

  val graphComposer = VersionGraphComposer.apply

  val appService: ApplicationService[F] = ApplicationService[F](
    applicationRepository = managerRepositories.applicationRepository,
    versionRepository = managerRepositories.modelVersionRepository,
    servableService = servableService,
    discoveryHub = discoveryHub,
    graphComposer
  )

  val modelService: ModelService[F] = ModelService[F](
    modelRepository = managerRepositories.modelRepository,
    modelVersionService = versionService,
    modelVersionRepository = managerRepositories.modelVersionRepository,
    storageService = modelStorage,
    appRepo = managerRepositories.applicationRepository,
    hostSelectorRepository = managerRepositories.hostSelectorRepository,
    fetcher = modelFetcher,
    modelVersionBuilder = versionBuilder
  )

}