package io.hydrosphere.serving.manager.discovery

import io.hydrosphere.serving.manager.util.topic.TopicPublisher

trait DiscoveryPublisher[F[_], T, K] extends TopicPublisher[F, DiscoveryEvent[T, K]]{
  type Event = DiscoveryEvent[T, K]

  def update(item: T): F[Unit] = publish(DiscoveryEvent.ItemUpdate(item :: Nil))
  def remove(itemId: K): F[Unit] = publish(DiscoveryEvent.ItemRemove(itemId :: Nil))
}


