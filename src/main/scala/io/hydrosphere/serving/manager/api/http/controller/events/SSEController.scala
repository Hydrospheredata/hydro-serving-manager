package io.hydrosphere.serving.manager.api.http.controller.events

import java.util.UUID
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.stream.scaladsl.Source
import cats.effect.{ConcurrentEffect, ContextShift}
import streamz.converter._
import io.circe.syntax._
import io.hydrosphere.serving.manager.api.http.controller.AkkaHttpControllerDsl
import io.hydrosphere.serving.manager.api.http.controller.application.ApplicationView
import io.hydrosphere.serving.manager.api.http.controller.servable.ServableView
import io.hydrosphere.serving.manager.discovery._
import io.hydrosphere.serving.manager.domain.application.ApplicationEvents
import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfigurationEvents
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionEvents}
import io.hydrosphere.serving.manager.domain.monitoring.MetricSpecEvents
import io.hydrosphere.serving.manager.domain.servable.ServableEvents

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class SSEController[F[_]](
    applicationSubscriber: ApplicationEvents.Subscriber[F],
    modelSubscriber: ModelVersionEvents.Subscriber[F],
    servableSubscriber: ServableEvents.Subscriber[F],
    metricSpecSubscriber: MetricSpecEvents.Subscriber[F],
    depSubscriber: DeploymentConfigurationEvents.Subscriber[F]
)(implicit
    F: ConcurrentEffect[F],
    cs: ContextShift[F]
) extends AkkaHttpControllerDsl {
  val ex                            = Executors.newCachedThreadPool()
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(ex)

  def subscribe =
    pathPrefix("events") {
      get {
        val id = UUID.randomUUID().toString
        complete {
          val appsSSE = applicationSubscriber.subscribe
            .flatMap(x => fs2.Stream.emits(SSEController.fromAppDiscovery(x)))

          val modelSSE = modelSubscriber.subscribe
            .flatMap(x => fs2.Stream.emits(SSEController.fromModelDiscovery(x)))

          val servableSSE = servableSubscriber.subscribe
            .flatMap(x => fs2.Stream.emits(SSEController.fromServableDiscovery(x)))

          val msSSE = metricSpecSubscriber.subscribe
            .flatMap(x => fs2.Stream.emits(SSEController.fromMetricSpecDiscovery(x)))

          val depSSE = depSubscriber.subscribe
            .flatMap(x => fs2.Stream.emits(SSEController.fromDepConfDiscovery(x)))

          val joined = appsSSE merge modelSSE merge servableSSE merge msSSE merge depSSE

          Source
            .fromGraph(joined.toSource)
            .keepAlive(5.seconds, () => ServerSentEvent.heartbeat)
            .watchTermination()((_, b) => b.foreach(_ => logger.debug(s"SSE $id terminated")))
        }
      }
    }

  val routes = subscribe

}

object SSEController {
  def fromDepConfDiscovery(x: DeploymentConfigurationEvents.Event): List[ServerSentEvent] =
    x match {
      case DiscoveryEvent.Initial => Nil
      case DiscoveryEvent.ItemUpdate(items) =>
        items.map { s =>
          ServerSentEvent(
            data = s.asJson.spaces2,
            `type` = "DeploymentConfigurationUpdate"
          )
        }
      case DiscoveryEvent.ItemRemove(items) =>
        items.map { s =>
          ServerSentEvent(
            data = s,
            `type` = "DeploymentConfigurationRemove"
          )
        }
    }

  def fromServableDiscovery(x: ServableEvents.Event): List[ServerSentEvent] =
    x match {
      case DiscoveryEvent.Initial => Nil
      case DiscoveryEvent.ItemUpdate(items) =>
        items.map { s =>
          ServerSentEvent(
            data = ServableView.fromServable(s).asJson.spaces2,
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

  def fromModelDiscovery(x: ModelVersionEvents.Event): List[ServerSentEvent] =
    x match {
      case DiscoveryEvent.Initial => Nil
      case DiscoveryEvent.ItemUpdate(items) =>
        items.map { mv =>
          ServerSentEvent(
            data = mv match {
              case m: ModelVersion.Internal => m.asJson.spaces2
              case m: ModelVersion.External => m.asJson.spaces2
            },
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

  def fromAppDiscovery(x: ApplicationEvents.Event): List[ServerSentEvent] =
    x match {
      case DiscoveryEvent.Initial => Nil
      case DiscoveryEvent.ItemUpdate(items) =>
        items.map { app =>
          ServerSentEvent(
            data = ApplicationView.fromApplication(app).asJson.spaces2,
            `type` = "ApplicationUpdate"
          )
        }
      case DiscoveryEvent.ItemRemove(items) =>
        items.map { i =>
          ServerSentEvent(
            data = i,
            `type` = "ApplicationRemove"
          )
        }
    }

  def fromMetricSpecDiscovery(x: MetricSpecEvents.Event): List[ServerSentEvent] =
    x match {
      case DiscoveryEvent.Initial => Nil
      case DiscoveryEvent.ItemUpdate(items) =>
        items.map { ms =>
          ServerSentEvent(
            data = ms.asJson.spaces2,
            `type` = "MetricSpecUpdate"
          )
        }
      case DiscoveryEvent.ItemRemove(items) =>
        items.map { ms =>
          ServerSentEvent(
            data = ms,
            `type` = "MetricSpecRemove"
          )
        }
    }
}
