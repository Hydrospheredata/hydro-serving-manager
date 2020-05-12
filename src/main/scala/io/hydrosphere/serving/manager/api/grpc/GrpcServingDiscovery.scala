package io.hydrosphere.serving.manager.api.grpc

import java.util.UUID

import cats.effect.ConcurrentEffect
import cats.effect.implicits._
import cats.implicits._
import com.google.protobuf.empty.Empty
import fs2.concurrent.SignallingRef
import io.grpc.stub.StreamObserver
import io.hydrosphere.serving.discovery.serving.ServingDiscoveryGrpc.ServingDiscovery
import io.hydrosphere.serving.discovery.serving._
import io.hydrosphere.serving.manager.discovery.DiscoveryEvent.{Initial, ItemRemove, ItemUpdate}
import io.hydrosphere.serving.manager.domain.application._
import io.hydrosphere.serving.manager.domain.monitoring.{MetricSpecEvents, MonitoringRepository}
import io.hydrosphere.serving.manager.domain.servable.{ServableEvents, ServableService}
import io.hydrosphere.serving.manager.infrastructure.grpc.Converters
import io.hydrosphere.serving.manager.util.UnsafeLogging

class GrpcServingDiscovery[F[_]](
    appSub: ApplicationEvents.Subscriber[F],
    servableSub: ServableEvents.Subscriber[F],
    metricSpecSub: MetricSpecEvents.Subscriber[F],
    appService: ApplicationService[F],
    servableService: ServableService[F],
    monitoringService: MonitoringRepository[F]
)(implicit F: ConcurrentEffect[F])
    extends ServingDiscovery
    with UnsafeLogging {

  private def runSync[A](f: => F[A]): A = f.toIO.unsafeRunSync()

  override def watchApplications(
      observer: StreamObserver[ApplicationDiscoveryEvent]
  ): StreamObserver[Empty] = {
    val id = UUID.randomUUID().toString
    logger.debug(s"Application watcher  $id registered")
    runSync {
      for {
        apps <- appService.all()
        initEvents = apps.grouped(10).toList.map { batch =>
          val converted =
            batch.collect {
              case x: Application if x.status == Application.Status.Ready =>
                Converters.fromApp(x)
            }
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
                val okApps = items.collect {
                  case x: Application if x.status == Application.Status.Ready =>
                    Converters.fromApp(x)
                }
                ApplicationDiscoveryEvent(added = okApps)
              case ItemRemove(items) =>
                ApplicationDiscoveryEvent(removedIds = items.map(_.toString))
            }
            .evalMap(x => F.delay(observer.onNext(x)))
            .compile
            .drain
            .start
      } yield new StreamObserver[Empty] {
        override def onNext(value: Empty): Unit = ()

        override def onError(t: Throwable): Unit = {
          logger.debug("Application stream failed", t)
          runSync(signal.set(true))
        }

        override def onCompleted(): Unit =
          runSync(signal.set(true))
      }
    }
  }

  override def watchServables(
      responseObserver: StreamObserver[ServableDiscoveryEvent]
  ): StreamObserver[Empty] = {
    val id = UUID.randomUUID().toString
    logger.debug(s"Servable subscriber $id registered")
    runSync {
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
          runSync(signal.set(true))
        }

        override def onCompleted(): Unit =
          runSync(signal.set(true))
      }
    }
  }

  override def watchMetricSpec(
      responseObserver: StreamObserver[MetricSpecDiscoveryEvent]
  ): StreamObserver[Empty] = {
    val id = UUID.randomUUID().toString
    logger.debug(s"MetricSpec subscriber $id registered")
    val flow = for {
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
        runSync(signal.set(true))
      }

      override def onCompleted(): Unit =
        runSync(signal.set(true))
    }
    runSync(flow)
  }
}
