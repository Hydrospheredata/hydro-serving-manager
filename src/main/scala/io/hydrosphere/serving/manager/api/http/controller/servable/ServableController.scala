package io.hydrosphere.serving.manager.api.http.controller.servable

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.stream.scaladsl.Source
import cats.effect.{ConcurrentEffect, ContextShift, Effect}
import cats.implicits._
import io.hydrosphere.serving.manager.api.http.controller.AkkaHttpControllerDsl
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.domain.servable.ServableService
import io.swagger.annotations._
import javax.ws.rs.Path
import streamz.converter._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

@Path("/servable")
@Api(produces = "application/json", tags = Array("Servable"))
class ServableController[F[_]](
  servableService: ServableService[F],
  cloudDriver: CloudDriver[F]
)(
  implicit F: ConcurrentEffect[F],
  cs: ContextShift[F],
  ec: ExecutionContext,
  actorSystem: ActorSystem,
) extends AkkaHttpControllerDsl {

  @Path("/")
  @ApiOperation(value = "servables", notes = "servables", nickname = "servables", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Servable", response = classOf[ServableView], responseContainer = "List"),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def listServables = path("servable") {
    get {
      completeF {
        servableService.all().map{ list =>
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
    new ApiResponse(code = 200, message = "Servable", response = classOf[ServableView]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def getServable = path("servable" / Segment) { name =>
    get {
      completeF {
        servableService.get(name)
          .map(ServableView.fromServable)
      }
    }
  }

  @Path("/{name}/logs")
  @ApiOperation(value = "get servable logs", notes = "get servable logs", nickname = "get-servable-logs", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "name", required = true, dataType = "string", paramType = "path", value = "name")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Logs", response = classOf[String]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def sseServableLogs = path("servable" / Segment / "logs") { name =>
    get {
      parameter('follow.as[Boolean].?) { follow: Option[Boolean] =>
        complete {
          val stream = cloudDriver.getLogs(name, follow.getOrElse(false)).map(ServerSentEvent(_))
          Source.fromGraph(stream.toSource)
            .keepAlive(5.seconds, () => ServerSentEvent.heartbeat)
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
    new ApiResponse(code = 200, message = "Servable", response = classOf[ServableView]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def deployModel = path("servable") {
    post {
      entity(as[DeployModelRequest]) { r =>
        completeF {
          servableService.findAndDeploy(r.modelName, r.version, r.deploymentConfigName, r.metadata.getOrElse(Map.empty))
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
    new ApiResponse(code = 200, message = "Servable", response = classOf[ServableView]),
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

  def routes = listServables ~ getServable ~ deployModel ~ stopServable ~ sseServableLogs
}