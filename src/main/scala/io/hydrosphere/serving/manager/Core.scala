package io.hydrosphere.serving.manager

import cats.effect._
import cats.implicits._
import io.hydrosphere.serving.manager.discovery._
import io.hydrosphere.serving.manager.domain.Monitor
import io.hydrosphere.serving.manager.domain.application._
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.domain.host_selector._
import io.hydrosphere.serving.manager.domain.image.ImageRepository
import io.hydrosphere.serving.manager.domain.model._
import io.hydrosphere.serving.manager.domain.model_build._
import io.hydrosphere.serving.manager.domain.model_version._
import io.hydrosphere.serving.manager.domain.servable._
import io.hydrosphere.serving.manager.infrastructure.grpc.PredictionClient
import io.hydrosphere.serving.manager.infrastructure.image.DockerImageBuilder
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.ModelFetcher
import io.hydrosphere.serving.manager.infrastructure.storage._
import io.hydrosphere.serving.manager.util.UUIDGenerator
import io.hydrosphere.serving.manager.util.random._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

case class Repositories[F[_]](
  appRepo: ApplicationRepository[F],
  hsRepo: HostSelectorRepository[F],
  modelRepo: ModelRepository[F],
  versionRepo: ModelVersionRepository[F],
  servableRepo: ServableRepository[F],
  buildLogRepo: BuildLogRepository[F]
)

final case class Core[F[_]](
  repos: Repositories[F],
  buildLoggingService: BuildLoggingService[F],
  hostSelectorService: HostSelectorService[F],
  modelService: ModelService[F],
  versionService: ModelVersionService[F],
  modelPub: ModelPublisher[F],
  modelSub: ModelSubscriber[F],
  appService: ApplicationService[F],
  appPub: ApplicationPublisher[F],
  appSub: ApplicationSubscriber[F],
  servableService: ServableService[F],
  servablePub: ServablePublisher[F],
  servableSub: ServableSubscriber[F]
)

object Core {
  def make[F[_]]()(
    implicit
    F: ConcurrentEffect[F],
    ec: ExecutionContext,
    timer: Timer[F],
    rng: RNG[F],
    storageOps: StorageOps[F],
    cloudDriver: CloudDriver[F],
    predictionCtor: PredictionClient.Factory[F],
    imageRepository: ImageRepository[F],
    imageBuilder: DockerImageBuilder[F],
    modelRepo: ModelRepository[F],
    modelVersionRepo: ModelVersionRepository[F],
    hostSelectorRepo: HostSelectorRepository[F],
    servableRepo: ServableRepository[F],
    appRepo: ApplicationRepository[F],
    buildLogsRepo: BuildLogRepository[F],
  ): F[Core[F]] = {
    implicit val servableProbe: ServableProbe[F] = ServableProbe.default[F]
    for {
      appPubSub <- DiscoveryTopic.make[F, Application, String]()
      modelPubSub <- DiscoveryTopic.make[F, ModelVersion, Long]()
      servablePubSub <- DiscoveryTopic.make[F, Servable, String]()
      buildLoggingService <- BuildLoggingService.make[F]()
      _ <- Monitor.monitoringLoop[F](servableRepo, appRepo)
      core = {
        implicit val (appPub, appSub) = appPubSub
        implicit val (modelPub, modelSub) = modelPubSub
        implicit val (servablePub, servableSub) = servablePubSub
        implicit val bl = buildLoggingService
        implicit val nameGen: NameGenerator[F] = NameGenerator.haiku[F]()
        implicit val uuidGen: UUIDGenerator[F] = UUIDGenerator.default[F]()
        implicit val modelUnpacker: ModelUnpacker[F] = ModelUnpacker.default[F]()
        implicit val modelFetcher: ModelFetcher[F] = ModelFetcher.default[F]()
        implicit val hostSelectorService: HostSelectorService[F] = HostSelectorService[F](hostSelectorRepo)
        implicit val versionService: ModelVersionService[F] = ModelVersionService[F]()
        implicit val versionBuilder: ModelVersionBuilder[F] = ModelVersionBuilder()
        implicit val servableService: ServableService[F] = ServableService[F]()
        implicit val appDeployer: ApplicationDeployer[F] = ApplicationDeployer.default()
        implicit val appService: ApplicationService[F] = ApplicationService[F]()
        implicit val modelService: ModelService[F] = ModelService[F]()

        val repos = Repositories(appRepo, hostSelectorRepo, modelRepo, modelVersionRepo, servableRepo, buildLogsRepo)
        Core(
          repos = repos,
          buildLoggingService = buildLoggingService,
          hostSelectorService = hostSelectorService,
          modelService = modelService,
          versionService = versionService,
          modelPub = modelPub,
          modelSub = modelSub,
          appService = appService,
          appPub = appPub,
          appSub = appSub,
          servableService = servableService,
          servablePub = servablePub,
          servableSub = servableSub
        )
      }
    } yield core
  }
}