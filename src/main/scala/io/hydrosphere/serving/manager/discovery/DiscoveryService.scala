package io.hydrosphere.serving.manager.discovery

import cats.implicits._
import cats.effect.Concurrent

trait DiscoveryService[T, K] {
  type Event            = DiscoveryEvent[T, K]
  type Publisher[F[_]]  = DiscoveryPublisher[F, T, K]
  type Subscriber[F[_]] = DiscoverySubscriber[F, T, K]

  case class PubSub[F[_]](pub: Publisher[F], sub: Subscriber[F])

  def makeTopic[F[_]: Concurrent]: F[PubSub[F]] =
    DiscoveryTopic.make[F, T, K]().map {
      case (pub, sub) => PubSub(pub, sub)
    }
}
