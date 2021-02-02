package io.hydrosphere.serving.manager.api.http.controller

import akka.http.scaladsl.server.Route
import akka.util.Timeout
import cats.effect.Effect
import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.domain.DomainError

import javax.ws.rs.Path
import scala.concurrent.duration._


// TODO(bulat): for backward compatibility only. delete after API change!
@Path("/hostSelector")
class HostSelectorController[F[_]: Effect] extends AkkaHttpControllerDsl {
  @JsonCodec
  case class OldHostSelector(id: Long, name: String, nodeSelector: Map[String, String])
  @JsonCodec
  case class CreateHostSelector(name: String, nodeSelector: Map[String, String])

  implicit val timeout = Timeout(5.seconds)

  @Path("/")
  def listHostSelectors = pathPrefix("hostSelector") {
    get {
      complete(List.empty[OldHostSelector])
    }
  }

  @Path("/")
  def createHostSelector = pathPrefix("hostSelector") {
    post {
      entity(as[CreateHostSelector]) { r =>
        complete(OldHostSelector(1, r.name, r.nodeSelector))
      }  
    }
  }

  @Path("/{envName}")
  def deleteHostSelector = delete {
    pathPrefix("hostSelector" / Segment) { _ =>
      complete("ok")
    }
  }

  @Path("/{envName}")
  def getHostSelector = get {
    pathPrefix("hostSelector" / Segment) { envName =>
      completeF(Effect[F].raiseError[OldHostSelector](DomainError.notFound("Not found")))
    }
  }

  val routes: Route = listHostSelectors ~ createHostSelector ~ deleteHostSelector ~ getHostSelector
}
