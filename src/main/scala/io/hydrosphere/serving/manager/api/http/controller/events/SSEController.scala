package io.hydrosphere.serving.manager.api.http.controller.events

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import cats.effect.implicits._
import cats.effect.{ConcurrentEffect, ContextShift}
import io.hydrosphere.serving.manager.api.http.controller.AkkaHttpControllerDsl
import io.hydrosphere.serving.manager.discovery._
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
        val appsSSE = apps.map(SSEEvents.fromAppDiscovery)

        val models = modelSubscriber.sub(id).toIO.unsafeRunSync()
        val modelSSE = models.map(SSEEvents.fromModelDiscovery)

        val joined = appsSSE.merge(modelSSE)
        val akkaJoined = Source.fromGraph(joined.toSource)

        akkaJoined
          .keepAlive(5.seconds, () => ServerSentEvent.heartbeat)
          .watchTermination() { (_, b) =>
            b.foreach(_ => logger.info(s"SSE ${id} terminated"))
          }
      }
    }
  }

  val routes = subscribe

}

object SSEEvents {
  def fromModelDiscovery[F[_]](x: ModelSubscriber[F]#Event): ServerSentEvent = {
    x match {
      case DiscoveryInitial => ServerSentEvent.heartbeat
      case DiscoverItemUpdate(items) =>
        ServerSentEvent(
          data = items.toString,
          `type` = "ModelUpdate"
        )
      case DiscoverItemRemove(items) =>
        ServerSentEvent(
          data = items.toString,
          `type` = "ModelRemove"
        )
    }
  }

  def fromAppDiscovery[F[_]](x: ApplicationSubscriber[F]#Event): ServerSentEvent = {
    x match {
      case DiscoveryInitial => ServerSentEvent.heartbeat
      case DiscoverItemUpdate(items) =>
        ServerSentEvent(
          data = items.toString(),
          `type` = "ApplicationUpdate"
        )
      case DiscoverItemRemove(items) =>
        ServerSentEvent(
          data = items.toString,
          `type` = "ApplicationRemove"
        )
    }
  }
}