package io.hydrosphere.serving.manager.api.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import cats.effect.{Async, ConcurrentEffect, ContextShift, Effect}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import io.hydrosphere.serving.BuildInfo
import io.hydrosphere.serving.manager.api.http.controller.application.ApplicationController
import io.hydrosphere.serving.manager.api.http.controller.events.SSEController
import io.hydrosphere.serving.manager.api.http.controller.host_selector.HostSelectorController
import io.hydrosphere.serving.manager.api.http.controller.model.ModelController
import io.hydrosphere.serving.manager.api.http.controller.servable.ServableController
import io.hydrosphere.serving.manager.api.http.controller.{AkkaHttpControllerDsl, SwaggerDocController}
import io.hydrosphere.serving.manager.config.{ApplicationConfig, ManagerConfiguration}
import io.hydrosphere.serving.manager.discovery.{ApplicationSubscriber, ModelSubscriber, ServableSubscriber}
import io.hydrosphere.serving.manager.domain.host_selector.HostSelectorService
import io.hydrosphere.serving.manager.util.AsyncUtil

import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext

trait HttpServer[F[_]] {
  def start(): F[Http.ServerBinding]
}

object HttpServer extends AkkaHttpControllerDsl {

  def akkaBased[F[_] : Async](
    config: ApplicationConfig,
    swaggerRoutes: Route,
    modelRoutes: Route,
    applicationRoutes: Route,
    hostSelectorRoutes: Route,
    servableRoutes: Route,
    sseRoutes: Route
  )(
    implicit as: ActorSystem,
    am: ActorMaterializer,
    ec: ExecutionContext
  ) = {
    val controllerRoutes: Route = pathPrefix("v2") {
      handleExceptions(commonExceptionHandler) {
        swaggerRoutes ~
          modelRoutes ~
          applicationRoutes ~
          hostSelectorRoutes ~
          servableRoutes ~
          sseRoutes
      }
    }

    val routes: Route = CorsDirectives.cors(CorsSettings.defaultSettings.copy(allowedMethods = Seq(GET, POST, HEAD, OPTIONS, PUT, DELETE))) {
      pathPrefix("health") {
        complete("OK")
      } ~
        pathPrefix("api") {
          controllerRoutes ~
            pathPrefix("buildinfo") {
              complete(HttpResponse(
                status = StatusCodes.OK,
                entity = HttpEntity(ContentTypes.`application/json`, BuildInfo.toJson)
              ))
            }
        } ~ pathPrefix("swagger") {
        path(Segments) { segs =>
          val path = segs.mkString("/")
          getFromResource(s"swagger/$path")
        }
      }
    }
    new HttpServer[F] {
      override def start(): F[Http.ServerBinding] = AsyncUtil.futureAsync {
        Http().bindAndHandle(routes, "0.0.0.0", config.port)
      }
    }
  }
}