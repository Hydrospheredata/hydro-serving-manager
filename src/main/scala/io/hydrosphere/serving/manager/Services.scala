package io.hydrosphere.serving.manager

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import cats.effect.{ConcurrentEffect, Timer}
import cats.implicits._
import cats.effect.implicits._
import com.spotify.docker.client._
import io.hydrosphere.serving.manager.config.{DockerClientConfig, ManagerConfiguration}
import io.hydrosphere.serving.manager.discovery.{ApplicationPublisher, ModelPublisher, ServablePublisher}
import io.hydrosphere.serving.manager.domain.application.{ApplicationDeployer, ApplicationService}
import io.hydrosphere.serving.manager.domain.application.graph.VersionGraphComposer
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.domain.host_selector.HostSelectorService
import io.hydrosphere.serving.manager.domain.image.ImageRepository
import io.hydrosphere.serving.manager.domain.model.ModelService
import io.hydrosphere.serving.manager.domain.model_build.{BuildLoggingService, ModelVersionBuilder}
import io.hydrosphere.serving.manager.domain.model_version.ModelVersionService
import io.hydrosphere.serving.manager.domain.servable.{ServableMonitor, ServableProbe, ServableService}
import io.hydrosphere.serving.manager.infrastructure.grpc.PredictionClient
import io.hydrosphere.serving.manager.infrastructure.image.DockerImageBuilder
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.ModelFetcher
import io.hydrosphere.serving.manager.infrastructure.storage.{LocalStorageOps, ModelUnpacker, StorageOps}
import io.hydrosphere.serving.manager.util.UUIDGenerator
import io.hydrosphere.serving.manager.util.docker.InfoProgressHandler
import io.hydrosphere.serving.manager.util.random.{NameGenerator, RNG}
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class Services[F[_]: ConcurrentEffect](
  val appPub: ApplicationPublisher[F],
  val servablePub: ServablePublisher[F],
  val modelPub: ModelPublisher[F],
  val managerRepositories: Repositories[F],
  val managerConfiguration: ManagerConfiguration,
  val dockerClient: DockerClient,
  val dockerClientConfig: DockerClientConfig,
  val cloudDriverService: CloudDriver[F],
  val predictionCtor: PredictionClient.Factory[F]
)(
  implicit ex: ExecutionContext,
  system: ActorSystem,
  materializer: ActorMaterializer,
  timeout: Timeout,
  timer: Timer[F],
  rng: RNG[F]
) extends Logging {

  import managerRepositories._

  val progressHandler = InfoProgressHandler

  implicit val nameGen = NameGenerator.haiku[F]()

  implicit val uuidGen = UUIDGenerator.default[F]()

  val storageOps: LocalStorageOps[F] = StorageOps.default[F]

  val modelStorage: ModelUnpacker[F] = ModelUnpacker[F](storageOps)

  val modelFetcher: ModelFetcher[F] = ModelFetcher.default[F](storageOps)

  implicit val buildLogging = BuildLoggingService.make().toIO.unsafeRunSync()

  val imageBuilder = new DockerImageBuilder(
    dockerClient = dockerClient,
    dockerClientConfig = dockerClientConfig,
    modelStorage = modelStorage,
  )

  val imageRepository: ImageRepository[F] =
    ImageRepository.fromConfig[F](dockerClient, progressHandler, managerConfiguration.dockerRepository)

  implicit val hostSelectorService: HostSelectorService[F] =
    HostSelectorService[F](managerRepositories.hostSelectorRepository)

  implicit val versionService: ModelVersionService[F] = ModelVersionService[F](
    modelVersionRepository = managerRepositories.modelVersionRepository,
    applicationRepo = managerRepositories.applicationRepository,
    modelPublisher = modelPub
  )

  val versionBuilder = ModelVersionBuilder(
    imageBuilder = imageBuilder,
    modelVersionRepository = managerRepositories.modelVersionRepository,
    imageRepository = imageRepository,
    modelVersionService = versionService,
    storageOps = storageOps,
    modelDiscoveryHub = modelPub,
    buildLoggingService = buildLogging
  )

  logger.info(s"Using ${cloudDriverService.getClass} cloud driver")

  implicit val c = predictionCtor
  implicit val cc = cloudDriverService
  implicit val servableProbe = ServableProbe.default[F]

  implicit val servableMonitor = ServableMonitor.default[F](
    2.seconds,
    1.minute
  ).toIO.unsafeRunSync().mon

  implicit val servableService: ServableService[F] = ServableService[F](
    cloudDriverService,
    managerRepositories.servableRepository,
    managerRepositories.applicationRepository,
    managerRepositories.modelVersionRepository,
    servableMonitor,
    servablePub
  )

  val graphComposer = VersionGraphComposer.default

  val appDeployer = ApplicationDeployer.default(
    servableService,
    managerRepositories.modelVersionRepository,
    managerRepositories.applicationRepository,
    graphComposer,
    appPub
  )

  implicit val appService: ApplicationService[F] = ApplicationService[F](
    applicationRepository = managerRepositories.applicationRepository,
    versionRepository = managerRepositories.modelVersionRepository,
    servableService = servableService,
    discoveryHub = appPub,
    applicationDeployer = appDeployer
  )

  implicit val modelService: ModelService[F] = ModelService[F](
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