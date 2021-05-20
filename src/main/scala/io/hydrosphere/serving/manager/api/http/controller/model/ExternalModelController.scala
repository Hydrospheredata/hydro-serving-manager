package io.hydrosphere.serving.manager.api.http.controller.model

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import cats.effect.Async
import cats.effect.std.Dispatcher
import io.hydrosphere.serving.manager.api.http.controller.AkkaHttpControllerDsl
import io.hydrosphere.serving.manager.domain.model.ModelService

import javax.ws.rs.Path

@Path("/externalmodel")
class ExternalModelController[F[_]](
    modelManagementService: ModelService[F]
)(implicit
    dispatcher: Dispatcher[F],
    system: ActorSystem,
    materializer: Materializer
) extends AkkaHttpControllerDsl {
  @Path("/")
  def registerModel: Route =
    pathPrefix("externalmodel") {
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

object ExternalModelController {
  def make[F[_]](
      modelService: ModelService[F]
  )(implicit
      F: Async[F],
      system: ActorSystem,
      materializer: Materializer
  ) =
    Dispatcher[F].map(implicit dispatcher => new ExternalModelController[F](modelService))
}
