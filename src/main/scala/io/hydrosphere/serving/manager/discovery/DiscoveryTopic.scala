package io.hydrosphere.serving.manager.discovery

import cats.effect.Concurrent
import cats.implicits._
import fs2.concurrent.Topic

object DiscoveryTopic {
  def make[F[_], T, K]()(
    implicit F: Concurrent[F]
  ): F[(DiscoveryPublisher[F, T, K], DiscoverySubscriber[F, T, K])] = {
    for {
      topic <- Topic[F, DiscoveryEvent[T, K]](DiscoveryEvent.Initial)
      sub = new DiscoverySubscriber[F, T, K] {
        override def subscribe: fs2.Stream[F, DiscoveryEvent[T, K]] = {
          topic.subscribe(32).drop(1)
        }
      }
      pub = new DiscoveryPublisher[F, T, K] {
        override def publish(t: DiscoveryEvent[T, K]): F[Unit] = topic.publish1(t)
      }
    } yield (pub, sub)
  }
}
