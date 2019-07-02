package io.hydrosphere.serving.manager.api.http.controller.servable

import akka.http.scaladsl.model.HttpEntity.ChunkStreamPart
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import cats.data.OptionT
import cats.effect.Effect
import cats.implicits._
import io.hydrosphere.serving.manager.api.http.controller.AkkaHttpControllerDsl
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.domain.servable.Servable.GenericServable
import io.hydrosphere.serving.manager.domain.servable.{ServableRepository, ServableService}
import io.swagger.annotations._
import javax.ws.rs.Path

@Path("/api/v2/servable")
@Api(produces = "application/json", tags = Array("Servable"))
class ServableController[F[_]]()(
  implicit F: Effect[F],
  servableService: ServableService[F],
  servableRepository: ServableRepository[F],
  cloudDriver: CloudDriver[F]
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
        servableRepository.all().map{ list =>
          list.map(ServableView.fromServable)
        }
      }
    }
  }

  @Path("/{name}")
  @ApiOperation(value = "get servable", notes = "get servable", nickname = "get-servable", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "name", required = true, dataType = "string", paramType = "path", value = "name")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Servable", response = classOf[GenericServable]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def getServable = path("servable" / Segment) { name =>
    get {
      completeF {
        OptionT(servableRepository.get(name))
          .map(ServableView.fromServable)
          .getOrElseF(F.raiseError[ServableView](DomainError.notFound(s"Can't find servable $name")))
      }
    }
  }
  
  @Path("/{name}/logs")
  @ApiOperation(value = "get servable logs", notes = "get servable logs", nickname="get-servable-logs", httpMethod="GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name="name", required=true, dataType="string", paramType = "path", value = "name")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Logs", response = classOf[String]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def getServableLogs = path("servable" / Segment / "logs") { name =>
    get {
      parameter('follow.as[Boolean].?) { follow: Option[Boolean] =>
        completeF {
          cloudDriver.getLogs(name, follow.getOrElse(false))
            .map(source => HttpEntity.Chunked(ContentTypes.`text/plain(UTF-8)`, source.map(ChunkStreamPart(_))))
        }
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
            .map(x => ServableView.fromServable(x.started))
        }
      }
    }
  }

  @Path("/{name}")
  @ApiOperation(value = "stop servable", notes = "stop servable", nickname = "stop-servable", httpMethod = "DELETE")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "name", required = true, dataType = "string", paramType = "path", value = "name")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Servable", response = classOf[GenericServable]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def stopServable = path("servable" / Segment) { name =>
    delete {
      completeF {
        servableService.stop(name)
          .map(ServableView.fromServable)
      }
    }
  }

  def routes = listServables ~ getServable ~ deployModel ~ stopServable ~ getServableLogs
}