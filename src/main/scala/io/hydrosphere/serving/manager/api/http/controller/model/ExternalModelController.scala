package io.hydrosphere.serving.manager.api.http.controller.model

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import cats.effect.{ConcurrentEffect, ContextShift}
import io.hydrosphere.serving.manager.api.http.controller.AkkaHttpControllerDsl
import io.hydrosphere.serving.manager.domain.model.{ModelRepository, ModelService}
import io.hydrosphere.serving.manager.domain.model_build.BuildLoggingService
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionService}
import io.swagger.annotations.{Api, ApiImplicitParam, ApiImplicitParams, ApiOperation, ApiResponse, ApiResponses}
import javax.ws.rs.Path

@Path("/api/v2/externalmodel")
@Api(produces = "application/json", tags = Array("External Model registration"))
class ExternalModelController[F[_]](
  modelManagementService: ModelService[F],
)(
  implicit F: ConcurrentEffect[F],
  cs: ContextShift[F],
  system: ActorSystem,
  materializer: ActorMaterializer,
) extends AkkaHttpControllerDsl {
  @Path("/")
  @ApiOperation(value = "Register an external model", notes = "Register an external model", nickname = "registerModel", httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "RegisterModelRequest", required = true,
      dataTypeClass = classOf[RegisterModelRequest], paramType = "body")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "ModelVersion", response = classOf[ModelVersion.External]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def registerModel = pathPrefix("externalmodel") {
    post {
      entity(as[RegisterModelRequest]) { req =>
        completeF {
          modelManagementService.registerModel(req)
        }
      }
    }
  }
  val routes: Route = registerModel
}