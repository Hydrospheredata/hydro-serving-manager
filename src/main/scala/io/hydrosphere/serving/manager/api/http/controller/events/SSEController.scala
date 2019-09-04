package io.hydrosphere.serving.manager.api.http.controller.events

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import cats.effect.{ConcurrentEffect, ContextShift}
import io.hydrosphere.serving.manager.api.http.controller.AkkaHttpControllerDsl
import io.hydrosphere.serving.manager.api.http.controller.application.ApplicationView
import io.hydrosphere.serving.manager.api.http.controller.servable.ServableView
import io.hydrosphere.serving.manager.discovery._
import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol
import spray.json._
import streamz.converter._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class SSEController[F[_]](
  applicationSubscriber: ApplicationSubscriber[F],
  modelSubscriber: ModelSubscriber[F],
  servableSubscriber: ServableSubscriber[F]
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
        val apps = applicationSubscriber.subscribe
        val appsSSE = apps.flatMap(x => fs2.Stream.emits(SSEController.fromAppDiscovery(x)))

        val models = modelSubscriber.subscribe
        val modelSSE = models.flatMap(x => fs2.Stream.emits(SSEController.fromModelDiscovery(x)))

        val servables = servableSubscriber.subscribe
        val servableSSE = servables.flatMap(x => fs2.Stream.emits(SSEController.fromServableDiscovery(x)))

        val joined = appsSSE merge modelSSE merge servableSSE

        Source.fromGraph(joined.toSource)
          .keepAlive(5.seconds, () => ServerSentEvent.heartbeat)
          .watchTermination() { (_, b) =>
            b.foreach { _ =>
              logger.debug(s"SSE $id terminated")
            }
          }
      }
    }
  }

  val routes = subscribe

}

object SSEController extends CompleteJsonProtocol {
  def fromServableDiscovery[F[_]](x: ServableSubscriber[F]#Event): List[ServerSentEvent] = {
    x match {
      case DiscoveryEvent.Initial => Nil
      case DiscoveryEvent.ItemUpdate(items) =>
        items.map { s =>
          ServerSentEvent(
            data = ServableView.fromServable(s).toJson.compactPrint,
            `type` = "ServableUpdate"
          )
        }
      case DiscoveryEvent.ItemRemove(items) =>
        items.map { s =>
          ServerSentEvent(
            data = s,
            `type` = "ServableRemove"
          )
        }
    }
  }

  def fromModelDiscovery[F[_]](x: ModelSubscriber[F]#Event): List[ServerSentEvent] = {
    x match {
      case DiscoveryEvent.Initial => Nil
      case DiscoveryEvent.ItemUpdate(items) =>
        items.map { mv =>
          ServerSentEvent(
            data = mv.toJson.compactPrint,
            `type` = "ModelUpdate"
          )
        }
      case DiscoveryEvent.ItemRemove(items) =>
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
      case DiscoveryEvent.Initial => Nil
      case DiscoveryEvent.ItemUpdate(items) =>
        items.map { app =>
          ServerSentEvent(
            data = ApplicationView.fromApplication(app).toJson.compactPrint,
            `type` = "ApplicationUpdate"
          )
        }
      case DiscoveryEvent.ItemRemove(items) =>
        items.map { i =>
          ServerSentEvent(
            data = i.toString,
            `type` = "ApplicationRemove"
          )
        }
    }
  }
}