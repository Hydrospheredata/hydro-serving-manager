package io.hydrosphere.serving.manager.api.http.controller.servable

import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import akka.http.scaladsl.model.sse.ServerSentEvent
import cats.effect.Effect
import cats.implicits._
import io.hydrosphere.serving.manager.api.http.controller.AkkaHttpControllerDsl
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.domain.servable.ServableService

import scala.concurrent.duration._


class ServableController[F[_]: Effect](
  servableService: ServableService[F],
  cloudDriver: CloudDriver[F]
) extends AkkaHttpControllerDsl {

  def listServables = path("servable") {
    get {
      completeF {
        servableService.all().map{ list =>
          list.map(ServableView.fromServable)
        }
      }
    }
  }

  def getServable = path("servable" / Segment) { name =>
    get {
      completeF {
        servableService.get(name)
          .map(ServableView.fromServable)
      }
    }
  }

  def sseServableLogs = path("servable" / Segment / "logs") { name =>
    get {
      parameter(Symbol("follow").as[Boolean].?) { follow: Option[Boolean] =>
        completeF {
          cloudDriver.getLogs(name, follow.getOrElse(false))
            .map(source => {
              source
                .map(ServerSentEvent(_))
                .keepAlive(5.seconds, () => ServerSentEvent.heartbeat)
            })
        }
      }
    }
  }

  def deployModel = path("servable") {
    post {
      entity(as[DeployModelRequest]) { r =>
        completeF {
          servableService.findAndDeploy(r.modelName, r.version, r.metadata.getOrElse(Map.empty))
            .map(x => ServableView.fromServable(x.started))
        }
      }
    }
  }

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