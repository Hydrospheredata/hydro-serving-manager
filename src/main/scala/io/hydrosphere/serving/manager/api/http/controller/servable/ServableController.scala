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
import skuber.Pod

import javax.ws.rs.Path
import streamz.converter._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import io.circe.syntax._

@Path("/servable")
class ServableController[F[_]](
    servableService: ServableService[F],
    cloudDriver: CloudDriver[F]
)(implicit
    F: ConcurrentEffect[F],
    cs: ContextShift[F],
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
