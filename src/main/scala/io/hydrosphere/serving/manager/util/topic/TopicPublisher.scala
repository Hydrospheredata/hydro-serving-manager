package io.hydrosphere.serving.manager.util.topic

trait TopicPublisher[F[_], T] {
  def publish(t: T): F[Unit]
}
