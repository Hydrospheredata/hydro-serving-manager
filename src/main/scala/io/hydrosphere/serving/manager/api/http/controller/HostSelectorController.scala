package io.hydrosphere.serving.manager.api.http.controller

import akka.http.scaladsl.server.Route
import akka.util.Timeout
import cats.effect.Effect
import io.hydrosphere.serving.manager.domain.DomainError
import io.swagger.annotations._
import javax.ws.rs.Path

import scala.concurrent.duration._


// TODO(bulat): for backward compatibility only. delete after API change!
@Path("/hostSelector")
@Api(produces = "application/json", tags = Array("Host Selectors"))
class HostSelectorController[F[_]: Effect] extends AkkaHttpControllerDsl {
  case class OldHostSelector(id: Long, name: String, nodeSelector: Map[String, String])
  case class CreateHostSelector(name: String, nodeSelector: Map[String, String])

  implicit val hsFormat = jsonFormat3(OldHostSelector.apply)
  implicit val createHsFormat = jsonFormat2(CreateHostSelector.apply)
  implicit val timeout = Timeout(5.seconds)

  @Path("/")
  @ApiOperation(value = "listHostSelectors", notes = "listHostSelectors", nickname = "listHostSelectors", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Serving Hosts", response = classOf[OldHostSelector], responseContainer = "List"),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def listHostSelectors = pathPrefix("hostSelector") {
    get {
      complete(List.empty[OldHostSelector])
    }
  }

  @Path("/")
  @ApiOperation(value = "createHostSelector", notes = "createHostSelector", nickname = "createHostSelector", httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "Host Object", required = true,
      dataTypeClass = classOf[CreateHostSelector], paramType = "body")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Host", response = classOf[OldHostSelector]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def createHostSelector = pathPrefix("hostSelector") {
    post {
      entity(as[CreateHostSelector]) { r =>
        complete(OldHostSelector(1, r.name, r.nodeSelector))
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
    pathPrefix("hostSelector" / Segment) { _ =>
      complete("ok")
    }
  }

  @Path("/{envName}")
  @ApiOperation(value = "getHostSelector", notes = "getHostSelector", nickname = "getHostSelector", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "envName", required = true, dataType = "string", paramType = "path", value = "envName")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, response = classOf[OldHostSelector], message = "HostSelector"),
    new ApiResponse(code = 404, message = "Not Found"),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def getHostSelector = get {
    pathPrefix("hostSelector" / Segment) { envName =>
      completeF(Effect[F].raiseError[OldHostSelector](DomainError.notFound("Not found")))
    }
  }

  val routes: Route = listHostSelectors ~ createHostSelector ~ deleteHostSelector ~ getHostSelector
}
