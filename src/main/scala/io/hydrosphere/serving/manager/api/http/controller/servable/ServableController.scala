package io.hydrosphere.serving.manager.api.http.controller.servable

import cats.data.OptionT
import cats.effect.Effect
import io.hydrosphere.serving.manager.api.http.controller.AkkaHttpControllerDsl
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.servable.Servable.GenericServable
import io.hydrosphere.serving.manager.domain.servable.{ServableRepository, ServableService}
import io.swagger.annotations._
import javax.ws.rs.Path

@Path("/api/v2/servable")
@Api(produces = "application/json", tags = Array("Servable"))
class ServableController[F[_]]()(
  implicit F: Effect[F],
  servableService: ServableService[F],
  servableRepository: ServableRepository[F]
) extends AkkaHttpControllerDsl {

  @Path("/")
  @ApiOperation(value = "servables", notes = "servables", nickname = "servables", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Servable", response = classOf[GenericServable], responseContainer = "List"),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def listServables = path("servable") {
    get {
      completeF {
        servableRepository.all()
      }
    }
  }

  @Path("/{name}")
  @ApiOperation(value = "get servable", notes = "get servable", nickname = "get-servable", httpMethod = "DELETE")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "servable name", required = true, dataType = "string", paramType = "path", value = "name")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Servable", response = classOf[GenericServable]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def getServables = path("servable" / Segment) { name =>
    get {
      completeF {
        OptionT(servableRepository.get(name))
          .getOrElseF(F.raiseError[GenericServable](DomainError.notFound(s"Can't find servable $name")))
      }
    }
  }

  @Path("/")
  @ApiOperation(value = "deploy servable", notes = "deploy servable", nickname = "deploy-servable", httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "DeployModelRequest", required = true, dataTypeClass = classOf[DeployModelRequest], paramType = "body")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Servable", response = classOf[GenericServable]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def deployModel = path("servable") {
    post {
      entity(as[DeployModelRequest]) { r =>
        completeF {
          servableService.findAndDeploy(r.modelName, r.version)
        }
      }
    }
  }

  @Path("/{name}")
  @ApiOperation(value = "stop servable", notes = "stop servable", nickname = "stop-servable", httpMethod = "DELETE")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "servable name", required = true, dataType = "string", paramType = "path", value = "name")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Servable", response = classOf[GenericServable]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def stopServable = path("servable" / Segment) { name =>
    delete {
      completeF {
        servableService.stop(name)
      }
    }
  }

  def routes = listServables ~ deployModel ~ stopServable
}