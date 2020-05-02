package io.hydrosphere.serving.manager.api.http.controller.model

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, Materializer}
import cats.effect.{ConcurrentEffect, ContextShift}
import io.hydrosphere.serving.manager.api.http.controller.AkkaHttpControllerDsl
import io.hydrosphere.serving.manager.domain.model.ModelService


class ExternalModelController[F[_]](
  modelManagementService: ModelService[F],
)(
  implicit F: ConcurrentEffect[F],
  cs: ContextShift[F],
  system: ActorSystem,
  materializer: Materializer,
) extends AkkaHttpControllerDsl {

  def registerModel: Route = pathPrefix("externalmodel") {
    post {
      entity(as[RegisterModelRequest]) { req =>
        completeF {
          modelManagementService.registerModel(req)
        }
      }
    }
  }
  val routes: Route = registerModel
}