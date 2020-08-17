package io.hydrosphere.serving.manager.api.http.controller

import akka.http.scaladsl.server.Route
import cats.effect.Effect
import io.hydrosphere.serving.manager.domain.deploy_config.{DeploymentConfiguration, DeploymentConfigurationService}
import io.swagger.annotations.{Api, ApiImplicitParam, ApiImplicitParams, ApiOperation, ApiResponse, ApiResponses}
import javax.ws.rs.Path

@Path("/deployment_configuration")
@Api(produces = "application/json", tags = Array("Metric Specifications"))
class DeploymentConfigController[F[_]: Effect](
  deploymentConfigService: DeploymentConfigurationService[F]
) extends AkkaHttpControllerDsl {

  @Path("/")
  @ApiOperation(value = "listAll", notes = "listAll", nickname = "listAll", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "MetricSpec", response = classOf[DeploymentConfiguration], responseContainer = "List"),
  ))
  val listAll: Route = path("deployment_configuration") {
    get {
      completeF(deploymentConfigService.all())
    }
  }

  @Path("/{name}")
  @ApiOperation(value = "getByName", notes = "getByName", nickname = "getByName", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "name", required = true, dataType = "string", paramType = "path", value = "name")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "DeploymentConfiguration", response = classOf[DeploymentConfiguration]),
    new ApiResponse(code = 404, message = "NotFound"),
  ))
  val getByName: Route = pathPrefix("deployment_configuration" / Segment) { name =>
    get {
      completeF(deploymentConfigService.get(name))
    }
  }

  @Path("/{name}")
  @ApiOperation(value = "deleteByName", notes = "deleteByName", nickname = "deleteByName", httpMethod = "DELETE")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "name", required = true, dataType = "string", paramType = "path", value = "name")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "DeploymentConfiguration", response = classOf[DeploymentConfiguration]),
    new ApiResponse(code = 404, message = "NotFound"),
  ))
  val deleteByName: Route = pathPrefix("deployment_configuration" / Segment) { name =>
    delete {
      completeF(deploymentConfigService.delete(name))
    }
  }

  @Path("/")
  @ApiOperation(value = "create", notes = "create", nickname = "create", httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "DeploymentConfiguration", required = true,
      dataTypeClass = classOf[DeploymentConfiguration], paramType = "body")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "DeploymentConfiguration", response = classOf[DeploymentConfiguration]),
    new ApiResponse(code = 400, message = "BadRequest"),
  ))
  val create: Route = pathPrefix("deployment_configuration") {
    post {
      entity(as[DeploymentConfiguration]) { config =>
        completeF(deploymentConfigService.create(config))
      }
    }
  }

  val routes: Route = getByName ~ deleteByName ~ create ~ listAll
}
