package io.hydrosphere.serving.manager.util.topic

trait TopicSubscriber[F[_], T] {
  def subscribe: fs2.Stream[F, T]
}
