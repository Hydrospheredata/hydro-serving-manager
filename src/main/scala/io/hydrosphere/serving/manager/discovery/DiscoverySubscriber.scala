package io.hydrosphere.serving.manager.discovery

import io.hydrosphere.serving.manager.util.topic.TopicSubscriber

trait DiscoverySubscriber[F[_], T, K] extends TopicSubscriber[F, DiscoveryEvent[T, K]]
