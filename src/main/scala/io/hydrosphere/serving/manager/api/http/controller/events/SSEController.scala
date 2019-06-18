package io.hydrosphere.serving.manager.api.http.controller.events

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import spray.json._
import cats.implicits._
import cats.effect.implicits._
import cats.effect.{ConcurrentEffect, ContextShift}
import io.hydrosphere.serving.manager.api.http.controller.AkkaHttpControllerDsl
import io.hydrosphere.serving.manager.api.http.controller.application.ApplicationView
import io.hydrosphere.serving.manager.discovery._
import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol
import streamz.converter._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class SSEController[F[_]](
  applicationSubscriber: ApplicationSubscriber[F],
  modelSubscriber: ModelSubscriber[F]
)(
  implicit F: ConcurrentEffect[F],
  cs: ContextShift[F],
  ec: ExecutionContext,
  actorSystem: ActorSystem,
) extends AkkaHttpControllerDsl {

  implicit val am = ActorMaterializer.create(actorSystem)

  def subscribe = pathPrefix("events") {
    get {
      val id = UUID.randomUUID().toString
      complete {
        val apps = applicationSubscriber.sub(id).toIO.unsafeRunSync()
        val appsSSE = apps.flatMap(x => fs2.Stream.emits(SSEEvents.fromAppDiscovery(x)))

        val models = modelSubscriber.sub(id).toIO.unsafeRunSync()
        val modelSSE = models.flatMap(x => fs2.Stream.emits(SSEEvents.fromModelDiscovery(x)))

        val joined = appsSSE.merge(modelSSE)
        val akkaJoined = Source.fromGraph(joined.toSource)

        akkaJoined
          .keepAlive(5.seconds, () => ServerSentEvent.heartbeat)
          .watchTermination() { (_, b) =>
            b.foreach { _ =>
              logger.debug(s"SSE ${id} terminated")
              val shutdown = applicationSubscriber.unsub(id) >>
                modelSubscriber.unsub(id)
              shutdown.toIO.unsafeRunSync()
            }
          }
      }
    }
  }

  val routes = subscribe

}

object SSEEvents extends CompleteJsonProtocol {
  def fromModelDiscovery[F[_]](x: ModelSubscriber[F]#Event): List[ServerSentEvent] = {
    x match {
      case DiscoveryInitial => ServerSentEvent.heartbeat :: Nil
      case DiscoverItemUpdate(items) =>
        items.map { mv =>
          ServerSentEvent(
            data = mv.toJson.compactPrint,
            `type` = "ModelUpdate"
          )
        }
      case DiscoverItemRemove(items) =>
        items.map { i =>
          ServerSentEvent(
            data = i.toString,
            `type` = "ModelRemove"
          )
        }
    }
  }

  def fromAppDiscovery[F[_]](x: ApplicationSubscriber[F]#Event): List[ServerSentEvent] = {
    x match {
      case DiscoveryInitial => ServerSentEvent.heartbeat :: Nil
      case DiscoverItemUpdate(items) =>
        items.map { app =>
          ServerSentEvent(
            data = ApplicationView.fromApplication(app).toJson.compactPrint,
            `type` = "ApplicationUpdate"
          )
        }
      case DiscoverItemRemove(items) =>
        items.map { i =>
          ServerSentEvent(
            data = i.toString,
            `type` = "ApplicationRemove"
          )
        }

    }
  }
}