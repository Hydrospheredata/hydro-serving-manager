package io.hydrosphere.serving.manager.discovery

import java.util.UUID

import cats.effect.Effect
import com.google.protobuf.empty.Empty
import io.grpc.stub.StreamObserver
import io.hydrosphere.serving.discovery.serving.{ApplicationDiscoveryEvent, ServableDiscoveryEvent}
import io.hydrosphere.serving.discovery.serving.ServingDiscoveryGrpc.ServingDiscovery
import io.hydrosphere.serving.manager.discovery.application.{AppsObserver, ObservedApplicationDiscoveryHub}
import io.hydrosphere.serving.manager.discovery.servable.{ObservedServableDiscoveryHub, ServableObserver}
import org.apache.logging.log4j.scala.Logging


object DiscoveryGrpc {

  class GrpcServingDiscovery[F[_]](
                                    appDiscoverer: ObservedApplicationDiscoveryHub[F],
                                    servableDiscoverer: ObservedServableDiscoveryHub[F]
                                  )(
                                    implicit F: Effect[F]
                                  ) extends ServingDiscovery with Logging {

    private def runSync[A](f: => F[A]): A = F.toIO(f).unsafeRunSync()

    override def watchApplications(observer: StreamObserver[ApplicationDiscoveryEvent]): StreamObserver[Empty] = {
      val id = UUID.randomUUID().toString

      val obs = AppsObserver.grpc[F](observer)
      runSync(appDiscoverer.register(id, obs))

      new StreamObserver[Empty] {

        override def onNext(value: Empty): Unit = ()

        override def onError(t: Throwable): Unit = {
          logger.debug("Client stream failed", t)
          runSync(appDiscoverer.unregister(id))
        }

        override def onCompleted(): Unit =
          runSync(appDiscoverer.unregister(id))
      }
    }

    override def watchServables(responseObserver: StreamObserver[ServableDiscoveryEvent]): StreamObserver[Empty] = {
      val id = UUID.randomUUID().toString
      val obs = ServableObserver.grpc[F](responseObserver)
      runSync(servableDiscoverer.register(id, obs))

      new StreamObserver[Empty] {
        override def onNext(value: Empty): Unit = ()

        override def onError(t: Throwable): Unit = {
          logger.debug("Client stream failed", t)
          runSync(servableDiscoverer.unregister(id))
        }

        override def onCompleted(): Unit = {
          runSync(servableDiscoverer.unregister(id))
        }
      }
    }
  }

}
