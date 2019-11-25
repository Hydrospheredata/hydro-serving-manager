package io.hydrosphere.serving.manager.discovery

import cats.effect.Concurrent

trait DiscoveryService[T, K] {
  type Event = DiscoveryEvent[T, K]
  type Publisher[F[_]] = DiscoveryPublisher[F, T, K]
  type Subscriber[F[_]] = DiscoverySubscriber[F, T, K]

  def makeTopic[F[_] : Concurrent]: F[(Publisher[F], Subscriber[F])] = {
    DiscoveryTopic.make[F, T, K]()
  }
}
