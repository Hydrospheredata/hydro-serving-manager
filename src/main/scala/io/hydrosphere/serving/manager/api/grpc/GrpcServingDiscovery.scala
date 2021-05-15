package io.hydrosphere.serving.manager.api.grpc

import java.util.UUID
import cats.effect.Async
import cats.effect.implicits._
import cats.effect.std.Dispatcher
import cats.implicits._
import com.google.protobuf.empty.Empty
import fs2.concurrent.SignallingRef
import io.grpc.stub.StreamObserver
import io.hydrosphere.serving.proto.discovery.api.ServingDiscoveryGrpc.ServingDiscovery
import io.hydrosphere.serving.proto.discovery.api.{
  ApplicationDiscoveryEvent,
  MetricSpecDiscoveryEvent,
  ServableDiscoveryEvent
}
import io.hydrosphere.serving.manager.discovery.DiscoveryEvent.{Initial, ItemRemove, ItemUpdate}
import io.hydrosphere.serving.manager.domain.application.{
  Application,
  ApplicationEvents,
  ApplicationService
}
import io.hydrosphere.serving.manager.domain.monitoring.{MetricSpecEvents, MonitoringRepository}
import io.hydrosphere.serving.manager.domain.servable.{ServableEvents, ServableService}
import io.hydrosphere.serving.manager.util.grpc.Converters
import org.apache.logging.log4j.scala.Logging

class GrpcServingDiscovery[F[_]](
    appSub: ApplicationEvents.Subscriber[F],
    servableSub: ServableEvents.Subscriber[F],
    metricSpecSub: MetricSpecEvents.Subscriber[F],
    appService: ApplicationService[F],
    servableService: ServableService[F],
    monitoringService: MonitoringRepository[F],
    dispatcher: Dispatcher[F]
)(implicit F: Async[F])
    extends ServingDiscovery
    with Logging {

  override def watchApplications(
      observer: StreamObserver[ApplicationDiscoveryEvent]
  ): StreamObserver[Empty] = {
    val id = UUID.randomUUID().toString
    logger.debug(s"Application watcher  $id registered")
    dispatcher.unsafeRunSync {
      for {
        apps <- appService.all()
        initEvents = apps.grouped(10).toList.map { batch =>
          val converted =
            batch.filter(_.status == Application.Status.Ready).map(x => Converters.fromApp(x))
          ApplicationDiscoveryEvent(added = converted)
        }
        _      <- initEvents.traverse(ev => F.delay(observer.onNext(ev)))
        signal <- SignallingRef[F, Boolean](false)
        stream = appSub.subscribe.interruptWhen(signal)
        _ <-
          stream
            .map {
              case Initial =>
                ApplicationDiscoveryEvent()
              case ItemUpdate(items) =>
                val okApps = items.filter(_.status == Application.Status.Ready).map { x =>
                  Converters.fromApp(x)
                }
                ApplicationDiscoveryEvent(added = okApps)
              case ItemRemove(items) =>
                ApplicationDiscoveryEvent(removedIds = items)
            }
            .evalMap(x => F.delay(observer.onNext(x)))
            .compile
            .drain
            .start
      } yield new StreamObserver[Empty] {
        override def onNext(value: Empty): Unit = ()

        override def onError(t: Throwable): Unit = {
          logger.debug("Application stream failed", t)
          dispatcher.unsafeRunSync(signal.set(true))
        }

        override def onCompleted(): Unit =
          dispatcher.unsafeRunSync(signal.set(true))
      }
    }
  }

  override def watchServables(
      responseObserver: StreamObserver[ServableDiscoveryEvent]
  ): StreamObserver[Empty] = {
    val id = UUID.randomUUID().toString
    logger.debug(s"Servable subscriber $id registered")
    dispatcher.unsafeRunSync {
      for {
        servables <- servableService.all()
        initEvents = servables.grouped(10).toList.map { batch =>
          val converted = batch.map(Converters.fromServable)
          ServableDiscoveryEvent(added = converted)
        }
        _      <- initEvents.traverse(ev => F.delay(responseObserver.onNext(ev)))
        signal <- SignallingRef[F, Boolean](false)
        _ <-
          servableSub.subscribe
            .interruptWhen(signal)
            .map {
              case Initial => ServableDiscoveryEvent()
              case ItemUpdate(items) =>
                ServableDiscoveryEvent(added = items.map(Converters.fromServable))
              case ItemRemove(items) => ServableDiscoveryEvent(removedIdx = items)
            }
            .evalMap(x => F.delay(responseObserver.onNext(x)))
            .compile
            .drain
            .start
      } yield new StreamObserver[Empty] {
        override def onNext(value: Empty): Unit = ()

        override def onError(t: Throwable): Unit = {
          logger.debug("Servable stream failed", t)
          dispatcher.unsafeRunSync(signal.set(true))
        }

        override def onCompleted(): Unit =
          dispatcher.unsafeRunSync(signal.set(true))
      }
    }
  }

  override def watchMetricSpec(
      responseObserver: StreamObserver[MetricSpecDiscoveryEvent]
  ): StreamObserver[Empty] = {
    val id = UUID.randomUUID().toString
    logger.debug(s"MetricSpec subscriber $id registered")
    dispatcher.unsafeRunSync {
      for {
        metrics <- monitoringService.all()
        initEvents = metrics.grouped(10).toList.map { batch =>
          val converted = batch.map(Converters.fromMetricSpec)
          MetricSpecDiscoveryEvent(added = converted)
        }
        _      <- initEvents.traverse(ev => F.delay(responseObserver.onNext(ev)))
        signal <- SignallingRef[F, Boolean](false)
        _ <-
          metricSpecSub.subscribe
            .interruptWhen(signal)
            .map {
              case Initial => MetricSpecDiscoveryEvent()
              case ItemUpdate(items) =>
                MetricSpecDiscoveryEvent(added = items.map(Converters.fromMetricSpec))
              case ItemRemove(items) => MetricSpecDiscoveryEvent(removedIdx = items)
            }
            .evalMap(x => F.delay(responseObserver.onNext(x)))
            .compile
            .drain
            .start
      } yield new StreamObserver[Empty] {
        override def onNext(value: Empty): Unit = ()

        override def onError(t: Throwable): Unit = {
          logger.debug("Servable stream failed", t)
          dispatcher.unsafeRunSync(signal.set(true))
        }

        override def onCompleted(): Unit =
          dispatcher.unsafeRunSync(signal.set(true))
      }
    }
  }
}

object GrpcServingDiscovery {
  def make[F[_]](
      appSub: ApplicationEvents.Subscriber[F],
      servableSub: ServableEvents.Subscriber[F],
      metricSpecSub: MetricSpecEvents.Subscriber[F],
      appService: ApplicationService[F],
      servableService: ServableService[F],
      monitoringService: MonitoringRepository[F]
  )(implicit F: Async[F]): F[GrpcServingDiscovery[F]] =
    Dispatcher[F].use { dispatcher =>
      F.pure(
        new GrpcServingDiscovery[F](
          appSub,
          servableSub,
          metricSpecSub,
          appService,
          servableService,
          monitoringService,
          dispatcher
        )
      )
    }
}
