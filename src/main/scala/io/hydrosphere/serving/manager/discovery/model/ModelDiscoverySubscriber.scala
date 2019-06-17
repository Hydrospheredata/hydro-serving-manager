package io.hydrosphere.serving.manager.discovery.model

import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.Stream
import fs2.concurrent.{SignallingRef, Topic}
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion

sealed trait ModelEvent
case class ModelUpdate(models: List[ModelVersion]) extends ModelEvent
case class ModelDelete(models: List[Long]) extends ModelEvent

trait ModelDiscoverySubscriber[F[_]] {
  def subscribe(id: String): F[Stream[F, ModelEvent]]
  def unsubscribe(id: String): F[Unit]
}

class TopicModelDiscoverySubscriber[F[_]](topic: Topic[F, ModelEvent])
  (implicit F: Concurrent[F]) extends ModelDiscoverySubscriber[F] {
  val state = Ref.unsafe[F, Map[String, SignallingRef[F, Boolean]]](Map.empty)

  override def subscribe(id: String) = {
    for {
      stopper <- SignallingRef.apply[F, Boolean](false)
      _ <- state.update { oldState =>
        oldState ++ Map(id -> stopper)
      }
    } yield topic.subscribe(32).interruptWhen(stopper)
  }

  override def unsubscribe(id: String): F[Unit] = {
    for {
      oldState <- state.get
      stopper <- F.fromOption(oldState.get(id), DomainError.invalidRequest(s"Can't find model observer $id"))
      _ <- stopper.set(true)
      _ <- state.update { os =>
        os - id
      }
    } yield ()
  }
}

//"""
//{
//  "type": "update",
//  "payload": [{}, {}]
//}
//
//{
//  "type": "delete",
//  "payload": [1, 2, 3]
//}
//"""