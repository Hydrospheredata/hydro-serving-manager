package io.hydrosphere.serving.manager.api.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import cats.effect.Async
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import io.hydrosphere.serving.BuildInfo
import io.hydrosphere.serving.manager.api.http.controller.AkkaHttpControllerDsl
import io.hydrosphere.serving.manager.config.ApplicationConfig
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
    sseRoutes: Route,
    monitoringRoutes: Route,
    externalModelRoutes: Route,
    deploymentConfRoutes: Route,
  )(implicit
    as: ActorSystem,
    am: ActorMaterializer,
    ec: ExecutionContext
  ): HttpServer[F] = {
    val controllerRoutes: Route = pathPrefix("v2") {
      handleExceptions(commonExceptionHandler) {
        swaggerRoutes ~
          modelRoutes ~
          externalModelRoutes ~
          applicationRoutes ~
          hostSelectorRoutes ~
          servableRoutes ~
          sseRoutes ~
          monitoringRoutes ~
          deploymentConfRoutes
      }
    }

    val swaggerUiRoutes = pathPrefix("swagger") {
      pathEndOrSingleSlash {
        redirect("/swagger/index.html", StatusCodes.TemporaryRedirect)
      } ~
        path(Segments) { segs =>
          val path = segs.mkString("/")
          getFromResource(s"swagger/$path")
        }
    }

    val buildInfoRoute = pathPrefix("buildinfo") {
      complete(HttpResponse(
        status = StatusCodes.OK,
        entity = HttpEntity(ContentTypes.`application/json`, BuildInfo.toJson)
      ))
    }

    val routes: Route = CorsDirectives.cors(CorsSettings.defaultSettings.copy(allowedMethods = Seq(GET, POST, HEAD, OPTIONS, PUT, DELETE))) {
      pathPrefix("health") {
        complete("OK")
      } ~
        pathPrefix("api") {
          controllerRoutes ~ buildInfoRoute
        } ~ swaggerUiRoutes
    }
    new HttpServer[F] {
      override def start(): F[Http.ServerBinding] = AsyncUtil.futureAsync {
        Http().bindAndHandle(routes, "0.0.0.0", config.port)
      }
    }
  }
}