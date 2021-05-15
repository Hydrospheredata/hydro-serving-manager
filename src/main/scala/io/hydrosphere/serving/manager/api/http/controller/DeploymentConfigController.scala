package io.hydrosphere.serving.manager.api.http.controller

import akka.http.scaladsl.server.Route
import cats.effect.Async
import cats.effect.std.Dispatcher
import io.hydrosphere.serving.manager.domain.deploy_config.{
  DeploymentConfiguration,
  DeploymentConfigurationService
}

import javax.ws.rs.Path

@Path("/deployment_configuration")
class DeploymentConfigController[F[_]: Dispatcher](
    deploymentConfigService: DeploymentConfigurationService[F]
) extends AkkaHttpControllerDsl {

  @Path("/")
  val listAll: Route = path("deployment_configuration") {
    get {
      completeF(deploymentConfigService.all())
    }
  }

  @Path("/{name}")
  val getByName: Route = pathPrefix("deployment_configuration" / Segment) { name =>
    get {
      completeF(deploymentConfigService.get(name))
    }
  }

  @Path("/{name}")
  val deleteByName: Route = pathPrefix("deployment_configuration" / Segment) { name =>
    delete {
      completeF(deploymentConfigService.delete(name))
    }
  }

  @Path("/")
  val create: Route = pathPrefix("deployment_configuration") {
    post {
      entity(as[DeploymentConfiguration]) { config =>
        completeF(deploymentConfigService.create(config))
      }
    }
  }

  val routes: Route = getByName ~ deleteByName ~ create ~ listAll
}

object DeploymentConfigController {
  def make[F[_]](
      deploymentConfigService: DeploymentConfigurationService[F]
  )(implicit F: Async[F]) =
    Dispatcher[F].map { implicit disp =>
      new DeploymentConfigController[F](deploymentConfigService)
    }
}
