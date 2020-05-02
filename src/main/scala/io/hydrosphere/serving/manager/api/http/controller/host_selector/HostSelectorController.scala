package io.hydrosphere.serving.manager.api.http.controller.host_selector

import akka.http.scaladsl.server.Route
import akka.util.Timeout
import cats.effect.Effect
import io.hydrosphere.serving.manager.api.http.controller.AkkaHttpControllerDsl
import io.hydrosphere.serving.manager.domain.host_selector.HostSelectorService

import scala.concurrent.duration._

class HostSelectorController[F[_]: Effect](
  hostSelectorService: HostSelectorService[F],
) extends AkkaHttpControllerDsl {
  implicit val timeout = Timeout(5.seconds)

  def listHostSelectors = pathPrefix("hostSelector") {
    get {
      completeF(hostSelectorService.all())
    }
  }

  def createHostSelector = pathPrefix("hostSelector") {
    post {
      entity(as[CreateHostSelector]) { r =>
        completeF(
          hostSelectorService.create(r.name, r.nodeSelector)
        )
      }  
    }
  }

  def deleteHostSelector = delete {
    pathPrefix("hostSelector" / Segment) { envName =>
      completeF(hostSelectorService.delete(envName))
    }
  }

  def getHostSelector = get {
    pathPrefix("hostSelector" / Segment) { envName =>
      completeF(hostSelectorService.get(envName))
    }
  }

  val routes: Route = listHostSelectors ~ createHostSelector ~ deleteHostSelector ~ getHostSelector
}
