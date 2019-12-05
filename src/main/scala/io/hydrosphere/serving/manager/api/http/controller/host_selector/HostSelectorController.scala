package io.hydrosphere.serving.manager.api.http.controller.host_selector

import akka.http.scaladsl.server.Route
import akka.util.Timeout
import cats.effect.Effect
import io.hydrosphere.serving.manager.api.http.controller.AkkaHttpControllerDsl
import io.hydrosphere.serving.manager.domain.host_selector.{HostSelector, HostSelectorRepository, HostSelectorService}
import io.swagger.annotations._
import javax.ws.rs.Path

import scala.concurrent.duration._


@Path("/hostSelector")
@Api(produces = "application/json", tags = Array("Host Selectors"))
class HostSelectorController[F[_]: Effect](
  hostSelectorService: HostSelectorService[F],
) extends AkkaHttpControllerDsl {
  implicit val timeout = Timeout(5.seconds)

  @Path("/")
  @ApiOperation(value = "listHostSelectors", notes = "listHostSelectors", nickname = "listHostSelectors", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Serving Hosts", response = classOf[HostSelector], responseContainer = "List"),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def listHostSelectors = pathPrefix("hostSelector") {
    get {
      completeF(hostSelectorService.all())
    }
  }

  @Path("/")
  @ApiOperation(value = "createHostSelector", notes = "createHostSelector", nickname = "createHostSelector", httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "Host Object", required = true,
      dataTypeClass = classOf[CreateHostSelector], paramType = "body")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Host", response = classOf[HostSelector]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def createHostSelector = pathPrefix("hostSelector") {
    post {
      entity(as[CreateHostSelector]) { r =>
        completeF(
          hostSelectorService.create(r.name, r.nodeSelector)
        )
      }  
    }
  }

  @Path("/{envName}")
  @ApiOperation(value = "deleteHostSelector", notes = "deleteHostSelector", nickname = "deleteHostSelector", httpMethod = "DELETE")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "envName", required = true, dataType = "string", paramType = "path", value = "envName")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Environment Deleted"),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def deleteHostSelector = delete {
    pathPrefix("hostSelector" / Segment) { envName =>
      completeF(hostSelectorService.delete(envName))
    }
  }

  @Path("/{envName}")
  @ApiOperation(value = "getHostSelector", notes = "getHostSelector", nickname = "getHostSelector", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "envName", required = true, dataType = "string", paramType = "path", value = "envName")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, response = classOf[HostSelector], message = "HostSelector"),
    new ApiResponse(code = 404, message = "Not Found"),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def getHostSelector = get {
    pathPrefix("hostSelector" / Segment) { envName =>
      completeF(hostSelectorService.get(envName))
    }
  }

  val routes: Route = listHostSelectors ~ createHostSelector ~ deleteHostSelector ~ getHostSelector
}
