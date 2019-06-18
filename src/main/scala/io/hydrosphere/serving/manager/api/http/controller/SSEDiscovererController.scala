package io.hydrosphere.serving.manager.api.http.controller

import java.util.UUID

import akka.NotUsed
import akka.stream.scaladsl.Source
import cats.effect.ConcurrentEffect
import io.hydrosphere.serving.manager.discovery.{ApplicationSubscriber, ModelSubscriber}

import scala.concurrent.duration._
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import akka.http.scaladsl.model.sse.ServerSentEvent

import scala.concurrent.ExecutionContext

class SSEDiscovererController[F[_]](
  applicationSubscriber: ApplicationSubscriber[F],
  modelSubscriber: ModelSubscriber[F]
)(
  implicit F: ConcurrentEffect[F],
  ec: ExecutionContext
) extends AkkaHttpControllerDsl {

  def subscribe = pathPrefix("events") {
    get {
      val id = UUID.randomUUID().toString
      complete {
        Source.tick(2.seconds, 2.seconds, NotUsed)
          .map(_ => ServerSentEvent(s"hi ${id}"))
          .keepAlive(5.seconds, () => ServerSentEvent.heartbeat)
          .watchTermination() { (a, b) =>
            b.foreach(_ => logger.info(s"SSE ${id} terminated"))
          }
      }
    }
  }

}