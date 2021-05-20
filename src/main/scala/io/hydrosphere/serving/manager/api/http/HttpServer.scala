package io.hydrosphere.serving.manager.api.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import cats.effect.Async
import cats.implicits._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import io.hydrosphere.serving.BuildInfo
import io.hydrosphere.serving.manager.Core
import io.hydrosphere.serving.manager.api.http.controller.application.ApplicationController
import io.hydrosphere.serving.manager.api.http.controller.model.{
  ExternalModelController,
  ModelController
}
import io.hydrosphere.serving.manager.api.http.controller.servable.ServableController
import io.hydrosphere.serving.manager.api.http.controller.{
  AkkaHttpControllerDsl,
  DeploymentConfigController,
  MonitoringController,
  SSEController
}
import io.hydrosphere.serving.manager.config.ApplicationConfig
import io.hydrosphere.serving.manager.domain.application.ApplicationEvents
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfigurationEvents
import io.hydrosphere.serving.manager.domain.model_version.ModelVersionEvents
import io.hydrosphere.serving.manager.domain.monitoring.MetricSpecEvents
import io.hydrosphere.serving.manager.domain.servable.ServableEvents

import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext

trait HttpServer[F[_]] {
  def start(): F[Http.ServerBinding]
}

object HttpServer extends AkkaHttpControllerDsl {

  def akkaBased[F[_]](
      config: ApplicationConfig,
      core: Core[F],
      cloudDriver: CloudDriver[F],
      appSub: ApplicationEvents.Subscriber[F],
      servSub: ServableEvents.Subscriber[F],
      modelSub: ModelVersionEvents.Subscriber[F],
      monitoringSub: MetricSpecEvents.Subscriber[F],
      depSub: DeploymentConfigurationEvents.Subscriber[F]
  )(implicit
      F: Async[F],
      as: ActorSystem,
      am: Materializer,
      ec: ExecutionContext
  ) =
    for {
      externalModelController <- ExternalModelController.make[F](core.modelService)

      modelController <- ModelController.make[F](
        core.modelService,
        core.repos.modelRepo,
        core.versionService,
        core.buildLoggingService
      )
      appController      <- ApplicationController.make[F](core.appService)
      servableController <- ServableController.make[F](core.servableService, cloudDriver)
      sseController <- SSEController.make[F](
        appSub,
        modelSub,
        servSub,
        monitoringSub,
        depSub
      )
      monitoringController <-
        MonitoringController.make[F](core.monitoringService, core.repos.monitoringRepository)
      depConfController <- DeploymentConfigController.make[F](core.deploymentConfigService)
      controllerRoutes: Route = pathPrefix("v2") {
        handleExceptions(commonExceptionHandler) {
          modelController.routes ~
            externalModelController.routes ~
            appController.routes ~
            servableController.routes ~
            sseController.routes ~
            monitoringController.routes ~
            depConfController.routes
        }
      }

      buildInfoRoute = pathPrefix("buildinfo") {
        complete(
          HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(ContentTypes.`application/json`, BuildInfo.toJson)
          )
        )
      }

      routes: Route = CorsDirectives.cors(
        CorsSettings.defaultSettings.withAllowedMethods(Seq(GET, POST, HEAD, OPTIONS, PUT, DELETE))
      ) {
        pathPrefix("health") {
          complete("OK")
        } ~
          pathPrefix("api") {
            controllerRoutes ~ buildInfoRoute
          }
      }
    } yield new HttpServer[F] {
      def start(): F[Http.ServerBinding] =
        F.fromFuture {
          F.delay {
            Http().newServerAt("0.0.0.0", config.port).bindFlow(routes)
          }
        }
    }
}
