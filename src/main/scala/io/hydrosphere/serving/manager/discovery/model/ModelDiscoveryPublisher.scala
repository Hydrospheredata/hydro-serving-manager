package io.hydrosphere.serving.manager.discovery.model

import cats.effect.Concurrent
import cats.implicits._
import fs2.concurrent.Topic
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion

trait ModelDiscoveryPublisher[F[_]] {
  def update(modelVersion: ModelVersion): F[Unit]
  def deleted(modelId: Long): F[Unit]
}

object ModelDiscoveryPublisher {
  def forTopic[F[_]](topic: Topic[F, ModelEvent])(implicit F: Concurrent[F]) = {
    val sub = new TopicModelDiscoverySubscriber[F](topic)
    val pub = new ModelDiscoveryPublisher[F] {
      override def update(modelVersion: ModelVersion): F[Unit] = topic.publish1(ModelUpdate(modelVersion :: Nil))

      override def deleted(modelId: Long): F[Unit] = topic.publish1(ModelDelete(modelId :: Nil))
    }
    (pub, sub)
  }

  def pubSub[F[_]](implicit F: Concurrent[F]): F[(ModelDiscoveryPublisher[F], TopicModelDiscoverySubscriber[F])] = {
    for {
      t <- Topic.apply[F, ModelEvent](ModelUpdate(Nil))
    } yield forTopic(t)
  }
}