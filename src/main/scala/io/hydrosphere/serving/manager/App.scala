package io.hydrosphere.serving.manager

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.util.Timeout
import cats.Monad
import cats.effect._
import com.zaxxer.hikari.HikariDataSource
import distage.ModuleDef
import io.hydrosphere.serving.discovery.serving.ServingDiscoveryGrpc.ServingDiscovery
import io.hydrosphere.serving.manager.api.ManagerServiceGrpc.ManagerService
import io.hydrosphere.serving.manager.api.grpc._
import io.hydrosphere.serving.manager.api.http.controller._
import io.hydrosphere.serving.manager.api.http.controller.application.ApplicationController
import io.hydrosphere.serving.manager.api.http.controller.events.SSEController
import io.hydrosphere.serving.manager.api.http.controller.host_selector.HostSelectorController
import io.hydrosphere.serving.manager.api.http.controller.model._
import io.hydrosphere.serving.manager.api.http.controller.servable.ServableController
import io.hydrosphere.serving.manager.api.http.{AkkaHttpServer, HttpServer}
import io.hydrosphere.serving.manager.config.ManagerConfiguration
import io.hydrosphere.serving.manager.domain.application._
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.domain.host_selector._
import io.hydrosphere.serving.manager.domain.image.ImageRepository
import io.hydrosphere.serving.manager.domain.model.{ModelRepository, ModelService}
import io.hydrosphere.serving.manager.domain.model_build._
import io.hydrosphere.serving.manager.domain.model_version._
import io.hydrosphere.serving.manager.domain.monitoring._
import io.hydrosphere.serving.manager.domain.servable.ServableMonitor.CancellableMonitor
import io.hydrosphere.serving.manager.domain.servable._
import io.hydrosphere.serving.manager.infrastructure.db.Database.HikariTransactor
import io.hydrosphere.serving.manager.infrastructure.db.repository._
import io.hydrosphere.serving.manager.infrastructure.db.{Database, FlywayClient}
import io.hydrosphere.serving.manager.infrastructure.docker.DockerdClient
import io.hydrosphere.serving.manager.infrastructure.grpc.{GrpcChannel, PredictionClient}
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.ModelFetcher
import io.hydrosphere.serving.manager.infrastructure.storage.{ModelUnpacker, StorageOps}
import io.hydrosphere.serving.manager.util.UUIDGenerator
import io.hydrosphere.serving.manager.util.random.{NameGenerator, RNG}
import izumi.fundamentals.reflection.Tags.TagK

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object App {
  def utilsModule[F[_]](
      config: ManagerConfiguration,
      dockerdClient: DockerdClient[F]
  )(implicit
      F: ConcurrentEffect[F],
      cs: ContextShift[F],
      timer: Timer[F],
      ec: ExecutionContext,
      as: ActorSystem,
      mat: Materializer,
      tagK: TagK[F]
  ) =
    new ModuleDef {
      make[GrpcChannel.Factory[F]].from[GrpcChannel.PlaintextFactory[F]]
      make[PredictionClient.Factory[F]].from[PredictionClient.DefaultFactory[F]]
      make[UUIDGenerator[F]].from(UUIDGenerator.default[F]())
      make[RNG[F]].fromEffect(RNG.default[F])

      make[Timeout].from(Timeout(5.minute))

      make[StorageOps[F]].from(StorageOps.default[F])
      make[CloudDriver[F]].from(CloudDriver.make[F] _)
      make[ImageRepository[F]].from(ImageRepository.make(dockerdClient, config.dockerRepository))
      make[HikariTransactor[F]].fromResource(Database.makeTransactor[F](config.database))
      make[FlywayClient[F]].fromEffect(FlywayClient.forTransactor[F, HikariDataSource] _)

      make[DBStarter[F]].from(DBStarter.make[F] _)
      make[NameGenerator[F]].from(NameGenerator.haiku[F] _)
    }

  def hostSelectorModule[F[_]](implicit F: Monad[F], tagK: TagK[F]) =
    new ModuleDef {
      make[HostSelectorRepository[F]].from[DBHostSelectorRepository[F]]
      make[HostSelectorService[F]].from(HostSelectorService[F] _)
    }

  def modelModule[F[_]](implicit F: Sync[F], clock: Clock[F], tagK: TagK[F]) =
    new ModuleDef {
      make[ModelRepository[F]].from[DBModelRepository[F]]
      make[ModelService[F]].from(ModelService[F] _)
      make[ModelUnpacker[F]].from(ModelUnpacker.default[F] _)
      make[ModelFetcher[F]].from(ModelFetcher.default[F] _)
    }

  def modelVersionModule[F[_]](implicit F: Concurrent[F], tagK: TagK[F]) =
    new ModuleDef {
      make[ModelVersionRepository[F]].from[DBModelVersionRepository[F]]
      make[ModelVersionService[F]].from(ModelVersionService[F] _)
      make[ModelVersionBuilder[F]].from(ModelVersionBuilder[F] _)

      make[ModelVersionEvents.PubSub[F]].fromEffect(ModelVersionEvents.makeTopic[F])
      make[ModelVersionEvents.Publisher[F]].from { x: ModelVersionEvents.PubSub[F] => x.pub }
      make[ModelVersionEvents.Subscriber[F]].from { x: ModelVersionEvents.PubSub[F] => x.sub }
    }

  def servableModule[F[_]](implicit
      F: Concurrent[F],
      timer: Timer[F],
      tagK: TagK[F]
  ) =
    new ModuleDef {
      make[ServableRepository[F]].from[DBServableRepository[F]]
      make[ServableService[F]].from(ServableService[F] _)
      make[ServableGC[F]].fromEffect(ServableGC.empty[F] _)
      make[ServableProbe[F]].from(ServableProbe.default[F] _)
      make[CancellableMonitor[F]].fromEffect(ServableMonitor.default[F] _)
      make[ServableMonitor[F]].from { x: CancellableMonitor[F] => x.mon }

      make[ServableEvents.PubSub[F]].fromEffect(ServableEvents.makeTopic[F])
      make[ServableEvents.Publisher[F]].from { x: ServableEvents.PubSub[F] => x.pub }
      make[ServableEvents.Subscriber[F]].from { x: ServableEvents.PubSub[F] => x.sub }
    }

  def applicationModule[F[_]](implicit F: Concurrent[F], tagK: TagK[F]) =
    new ModuleDef {
      make[ApplicationRepository[F]].from[DBApplicationRepository[F]]
      make[ApplicationService[F]].from(ApplicationService[F] _)
      make[ApplicationDeployer[F]].from(ApplicationDeployer.default[F] _)

      make[ApplicationEvents.PubSub[F]].fromEffect(ApplicationEvents.makeTopic[F])
      make[ApplicationEvents.Publisher[F]].from { x: ApplicationEvents.PubSub[F] => x.pub }
      make[ApplicationEvents.Subscriber[F]].from { x: ApplicationEvents.PubSub[F] => x.sub }
    }

  def buildLogModule[F[_]](implicit F: ConcurrentEffect[F], tagK: TagK[F]) =
    new ModuleDef {
      make[BuildLogRepository[F]].from[DBBuildLogRepository[F]]
      make[BuildLoggingService[F]].fromEffect(BuildLoggingService.make[F] _)
    }

  def monitoringModule[F[_]](implicit F: Concurrent[F], tagK: TagK[F]) =
    new ModuleDef {
      make[MonitoringRepository[F]].from[DBMonitoringRepository[F]]
      make[MonitoringService[F]].from(MonitoringService[F] _)

      make[MetricSpecEvents.PubSub[F]].fromEffect(MetricSpecEvents.makeTopic[F])
      make[MetricSpecEvents.Publisher[F]].from { x: MetricSpecEvents.PubSub[F] => x.pub }
      make[MetricSpecEvents.Subscriber[F]].from { x: MetricSpecEvents.PubSub[F] => x.sub }
    }

  def grpcModule[F[_]](implicit F: Sync[F], ex: ExecutionContext, tagK: TagK[F]) =
    new ModuleDef {
      make[ServingDiscovery].from[GrpcServingDiscovery[F]]
      make[ManagerService].from[ManagerGrpcService[F]]
      make[GrpcServer[F]].fromEffect(GrpcServer.default[F] _)
    }

  def httpModule[F[_]](implicit tagK: TagK[F]) =
    new ModuleDef {
      make[ExternalModelController[F]]
      make[ModelController[F]]
      make[ApplicationController[F]]
      make[HostSelectorController[F]]
      make[ServableController[F]]
      make[MonitoringController[F]]
      make[SwaggerDocController[F]]
      make[SSEController[F]]
      make[HttpServer[F]].from[AkkaHttpServer[F]]
    }

  def allModules[F[_]](
      config: ManagerConfiguration,
      dockerdClient: DockerdClient[F]
  )(implicit
      F: ConcurrentEffect[F],
      cs: ContextShift[F],
      timer: Timer[F],
      ec: ExecutionContext,
      as: ActorSystem,
      mat: Materializer,
      tagK: TagK[F]
  ) =
    utilsModule[F](config, dockerdClient) ++ hostSelectorModule[F] ++
      modelModule[F] ++ modelVersionModule[F] ++ buildLogModule[F] ++
      servableModule[F] ++ applicationModule[F] ++ monitoringModule[F] ++
      httpModule[F] ++ grpcModule[F]
}
