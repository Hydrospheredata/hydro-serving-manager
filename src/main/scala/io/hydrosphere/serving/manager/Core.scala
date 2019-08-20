package io.hydrosphere.serving.manager

import cats.effect._
import cats.implicits._
import io.hydrosphere.serving.manager.discovery.{ApplicationPublisher, ApplicationSubscriber, DiscoveryTopic, ModelPublisher, ModelSubscriber, ServablePublisher, ServableSubscriber}
import io.hydrosphere.serving.manager.domain.application.Application.GenericApplication
import io.hydrosphere.serving.manager.domain.application.graph.VersionGraphComposer
import io.hydrosphere.serving.manager.domain.application.{ApplicationDeployer, ApplicationRepository, ApplicationService}
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.domain.host_selector.{HostSelectorRepository, HostSelectorService}
import io.hydrosphere.serving.manager.domain.image.ImageRepository
import io.hydrosphere.serving.manager.domain.model.{ModelRepository, ModelService}
import io.hydrosphere.serving.manager.domain.model_build.{BuildLogRepository, BuildLoggingService, ModelVersionBuilder}
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionRepository, ModelVersionService}
import io.hydrosphere.serving.manager.domain.servable.Servable.GenericServable
import io.hydrosphere.serving.manager.domain.servable.{ServableMonitor, ServableProbe, ServableRepository, ServableService}
import io.hydrosphere.serving.manager.infrastructure.grpc.PredictionClient
import io.hydrosphere.serving.manager.infrastructure.image.DockerImageBuilder
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.ModelFetcher
import io.hydrosphere.serving.manager.infrastructure.storage.{ModelUnpacker, StorageOps}
import io.hydrosphere.serving.manager.util.UUIDGenerator
import io.hydrosphere.serving.manager.util.random.{NameGenerator, RNG}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


final case class Core[F[_]](
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
    for {
      appPubSub <- DiscoveryTopic.make[F, GenericApplication, String]()
      modelPubSub <- DiscoveryTopic.make[F, ModelVersion, Long]()
      servablePubSub <- DiscoveryTopic.make[F, GenericServable, String]()
      buildLoggingService <- BuildLoggingService.make[F]()
      core <- {
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
        implicit val servableProbe: ServableProbe[F] = ServableProbe.default[F]
        for {
          servableMonitor <- ServableMonitor.default[F](2.seconds, 1.minute)
        } yield {
          implicit val sm = servableMonitor.mon
          implicit val versionBuilder: ModelVersionBuilder[F] = ModelVersionBuilder()
          implicit val servableService: ServableService[F] = ServableService[F]()
          implicit val graphComposer: VersionGraphComposer = VersionGraphComposer.default
          implicit val appDeployer: ApplicationDeployer[F] = ApplicationDeployer.default()
          implicit val appService: ApplicationService[F] = ApplicationService[F]()
          implicit val modelService: ModelService[F] = ModelService[F]()
          Core(
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
      }
    } yield core
  }
}