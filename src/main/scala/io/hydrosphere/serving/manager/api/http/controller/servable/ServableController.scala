package io.hydrosphere.serving.manager.api.http.controller.servable

import cats.effect.Effect
import io.hydrosphere.serving.manager.api.http.controller.AkkaHttpControllerDsl
import io.hydrosphere.serving.manager.domain.servable.{ServableRepository, ServableService}

case class DeployModelRequest(modelName: String, version: Long)

class ServableController[F[_]: Effect]()(
  implicit servableService: ServableService[F],
  servableRepository: ServableRepository[F]
) extends AkkaHttpControllerDsl {
  def listServables = path("servable") {
    get {
      completeF {
        servableRepository.all()
      }
    }
  }

  def deployModel = path("servable") {
    post {
      entity(as[DeployModelRequest]) { r =>
        completeF {
          servableService.findAndDeploy(r.modelName, r.version)
        }
      }
    }
  }

  def stopServable = path("servable" / Segment) { name =>
    delete {
      completeF {
        servableService.stop(name)
      }
    }
  }

  def routes = listServables ~ deployModel ~ stopServable
}