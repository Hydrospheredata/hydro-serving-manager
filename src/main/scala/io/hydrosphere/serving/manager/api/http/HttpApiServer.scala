package io.hydrosphere.serving.manager.api.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import cats.effect.Effect
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import io.hydrosphere.serving.BuildInfo
import io.hydrosphere.serving.manager.api.http.controller.host_selector.HostSelectorController
import io.hydrosphere.serving.manager.api.http.controller.model.ModelController
import io.hydrosphere.serving.manager.api.http.controller.servable.ServableController
import io.hydrosphere.serving.manager.api.http.controller.{AkkaHttpControllerDsl, ApplicationController, SwaggerDocController}
import io.hydrosphere.serving.manager.config.ManagerConfiguration
import io.hydrosphere.serving.manager.util.AsyncUtil
import io.hydrosphere.serving.manager.{Repositories, Services}

import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext

class HttpApiServer[F[_]: Effect](
  managerRepositories: Repositories[F],
  managerServices: Services[F],
  managerConfiguration: ManagerConfiguration
)(
  implicit system: ActorSystem,
  materializer: ActorMaterializer,
  ec: ExecutionContext
) extends AkkaHttpControllerDsl {

  import managerRepositories._
  import managerServices._

  val environmentController = new HostSelectorController[F]()

  val modelController = new ModelController[F]()

  val applicationController = new ApplicationController()

  val servableController = new ServableController[F]()

  val swaggerController = new SwaggerDocController(
    Set(
      classOf[HostSelectorController[F]],
      classOf[ModelController[F]],
      classOf[ApplicationController[F]],
      classOf[ServableController[F]]
    ),
    "2"
  )

  val controllerRoutes: Route = pathPrefix("v2") {
    handleExceptions(commonExceptionHandler) {
      swaggerController.routes ~
        modelController.routes ~
        applicationController.routes ~
        environmentController.routes ~
        servableController.routes
    }
  }

  def routes: Route = CorsDirectives.cors(
    CorsSettings.defaultSettings.copy(allowedMethods = Seq(GET, POST, HEAD, OPTIONS, PUT, DELETE))
  ) {
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

  def start(): F[Http.ServerBinding] = AsyncUtil.futureAsync {
    Http().bindAndHandle(routes, "0.0.0.0", managerConfiguration.application.port)
  }
}