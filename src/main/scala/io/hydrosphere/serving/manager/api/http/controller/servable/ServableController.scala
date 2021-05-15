package io.hydrosphere.serving.manager.api.http.controller.servable

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.stream.scaladsl.Source
import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import cats.implicits._
import io.hydrosphere.serving.manager.api.http.controller.AkkaHttpControllerDsl
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.domain.servable.ServableService

import javax.ws.rs.Path
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import streamz.converter._

@Path("/servable")
class ServableController[F[_]](
    servableService: ServableService[F],
    cloudDriver: CloudDriver[F]
)(implicit
    F: Async[F],
    dispatcher: Dispatcher[F],
    ec: ExecutionContext,
    actorSystem: ActorSystem
) extends AkkaHttpControllerDsl {

  @Path("/")
  def listServables =
    path("servable") {
      get {
        completeF {
          servableService.all().map(list => list.map(ServableView.fromServable))
        }
      }
    }

  @Path("/{name}")
  def getServable =
    path("servable" / Segment) { name =>
      get {
        completeF {
          servableService
            .get(name)
            .map(ServableView.fromServable)
        }
      }
    }

  @Path("/{name}/logs")
  def sseServableLogs =
    path("servable" / Segment / "logs") { name =>
      get {
        parameter(Symbol("follow").as[Boolean].?) { follow: Option[Boolean] =>
          complete {
            val stream = cloudDriver.getLogs(name, follow.getOrElse(false)).map(ServerSentEvent(_))
            Source
              .fromGraph(stream.toSource)
              .keepAlive(5.seconds, () => ServerSentEvent.heartbeat)
          }
        }
      }
    }

  @Path("/")
  def deployModel =
    path("servable") {
      post {
        entity(as[DeployModelRequest]) { r =>
          completeF {
            servableService
              .findAndDeploy(
                r.modelName,
                r.version,
                r.deploymentConfigName,
                r.metadata.getOrElse(Map.empty)
              )
              .map(x => ServableView.fromServable(x))
          }
        }
      }
    }

  @Path("/{name}")
  def stopServable =
    path("servable" / Segment) { name =>
      delete {
        completeF {
          servableService
            .stop(name)
            .map(ServableView.fromServable)
        }
      }
    }

  def routes =
    listServables ~ getServable ~ deployModel ~ stopServable ~ sseServableLogs
}

object ServableController {
  def make[F[_]](
      servableService: ServableService[F],
      cloudDriver: CloudDriver[F]
  )(implicit
      F: Async[F],
      ec: ExecutionContext,
      actorSystem: ActorSystem
  ) = Dispatcher[F].map(implicit disp => new ServableController[F](servableService, cloudDriver))
}
