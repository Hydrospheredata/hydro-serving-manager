package io.hydrosphere.serving.manager.api.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import cats.effect.Async
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import io.hydrosphere.serving.BuildInfo
import io.hydrosphere.serving.manager.api.http.controller.application.ApplicationController
import io.hydrosphere.serving.manager.api.http.controller.events.SSEController
import io.hydrosphere.serving.manager.api.http.controller.host_selector.HostSelectorController
import io.hydrosphere.serving.manager.api.http.controller.model.{
  ExternalModelController,
  ModelController
}
import io.hydrosphere.serving.manager.api.http.controller.servable.ServableController
import io.hydrosphere.serving.manager.api.http.controller.{
  AkkaHttpControllerDsl,
  MonitoringController,
  SwaggerDocController
}
import io.hydrosphere.serving.manager.config.ApplicationConfig
import io.hydrosphere.serving.manager.util.AsyncUtil

import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext

trait HttpServer[F[_]] {
  def start(): F[Http.ServerBinding]
}

class AkkaHttpServer[F[_]: Async](
    config: ApplicationConfig,
    swaggerCtr: SwaggerDocController[F],
    modelCtr: ModelController[F],
    applicationCtr: ApplicationController[F],
    hostSelectorCtr: HostSelectorController[F],
    servableCtr: ServableController[F],
    sseCtr: SSEController[F],
    monitoringCtr: MonitoringController[F],
    externalModelCtr: ExternalModelController[F]
)(implicit
    as: ActorSystem,
    am: Materializer,
    ec: ExecutionContext
) extends HttpServer[F]
    with AkkaHttpControllerDsl {
  val controllerRoutes: Route = pathPrefix("v2") {
    handleExceptions(commonExceptionHandler) {
      swaggerCtr.routes ~
        modelCtr.routes ~
        externalModelCtr.routes ~
        applicationCtr.routes ~
        hostSelectorCtr.routes ~
        servableCtr.routes ~
        sseCtr.routes ~
        monitoringCtr.routes
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
    complete(
      HttpResponse(
        status = StatusCodes.OK,
        entity = HttpEntity(ContentTypes.`application/json`, BuildInfo.toJson)
      )
    )
  }

  val corsSettings =
    CorsSettings.defaultSettings.withAllowedMethods(Seq(GET, POST, HEAD, OPTIONS, PUT, DELETE))

  val routes: Route = CorsDirectives.cors(corsSettings) {
    pathPrefix("health") {
      complete("OK")
    } ~
      pathPrefix("api") {
        controllerRoutes ~ buildInfoRoute
      } ~ swaggerUiRoutes
  }

  override def start(): F[Http.ServerBinding] =
    AsyncUtil.futureAsync {
      Http().bindAndHandle(routes, "0.0.0.0", config.port)
    }
}
