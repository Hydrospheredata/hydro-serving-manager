package io.hydrosphere.serving.manager.api.grpc

import java.util.UUID

import cats.effect.ConcurrentEffect
import cats.effect.implicits._
import cats.implicits._
import com.google.protobuf.empty.Empty
import io.grpc.stub.StreamObserver
import io.hydrosphere.serving.discovery.serving.ServingDiscoveryGrpc.ServingDiscovery
import io.hydrosphere.serving.discovery.serving.{ApplicationDiscoveryEvent, ServableDiscoveryEvent}
import io.hydrosphere.serving.manager.discovery.{ApplicationSubscriber, DiscoverItemRemove, DiscoverItemUpdate, DiscoveryInitial, ServableSubscriber}
import io.hydrosphere.serving.manager.domain.application.Application
import io.hydrosphere.serving.manager.domain.application.Application.ReadyApp
import io.hydrosphere.serving.manager.util.grpc.Converters
import org.apache.logging.log4j.scala.Logging


class GrpcServingDiscovery[F[_]](
  appSub: ApplicationSubscriber[F],
  servableSub: ServableSubscriber[F]
)(
  implicit F: ConcurrentEffect[F]
) extends ServingDiscovery with Logging {

  private def runSync[A](f: => F[A]): A = f.toIO.unsafeRunSync()

  override def watchApplications(observer: StreamObserver[ApplicationDiscoveryEvent]): StreamObserver[Empty] = {
    val id = UUID.randomUUID().toString
    logger.debug(s"Application watcher  $id registered")
    runSync {
      for {
        stream <- appSub.sub(id)
        _ <- stream.map {
          case DiscoveryInitial =>
            ApplicationDiscoveryEvent()
          case DiscoverItemUpdate(items) =>
            val okApps = items.filter(_.status.isInstanceOf[Application.Ready]).map { x =>
              Converters.fromApp(x.asInstanceOf[ReadyApp])
            }
            ApplicationDiscoveryEvent(added = okApps)
          case DiscoverItemRemove(items) => ApplicationDiscoveryEvent(removedIds = items.map(_.toString))
        }.evalMap(x => F.delay(observer.onNext(x))).compile.drain.start
      } yield ()
    }

    new StreamObserver[Empty] {
      override def onNext(value: Empty): Unit = ()

      override def onError(t: Throwable): Unit = {
        logger.debug("Application stream failed", t)
        runSync(appSub.unsub(id))
      }

      override def onCompleted(): Unit =
        runSync(appSub.unsub(id))
    }

  }

  override def watchServables(responseObserver: StreamObserver[ServableDiscoveryEvent]): StreamObserver[Empty] = {
    val id = UUID.randomUUID().toString
    logger.debug(s"Servable subscriber $id registered")
    runSync {
      for {
        stream <- servableSub.sub(id)
        _ <- stream.map {
          case DiscoveryInitial => ServableDiscoveryEvent()
          case DiscoverItemUpdate(items) => ServableDiscoveryEvent(added = items.map(Converters.fromServable))
          case DiscoverItemRemove(items) => ServableDiscoveryEvent(removedIdx = items)
        }.evalMap(x => F.delay(responseObserver.onNext(x)))
          .compile.drain.start
      } yield ()
    }

    new StreamObserver[Empty] {
      override def onNext(value: Empty): Unit = ()

      override def onError(t: Throwable): Unit = {
        logger.debug("Servable stream failed", t)
        runSync(servableSub.unsub(id))
      }

      override def onCompleted(): Unit = {
        runSync(servableSub.unsub(id))
      }
    }
  }
}