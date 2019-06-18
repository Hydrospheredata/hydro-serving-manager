package io.hydrosphere.serving.manager.discovery

import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.concurrent.{SignallingRef, Topic}
import io.hydrosphere.serving.manager.domain.DomainError


sealed trait DiscoveryEvent[+T, +K]
case object DiscoveryInitial extends DiscoveryEvent[Nothing, Nothing]
case class DiscoverItemUpdate[T, K](items: List[T]) extends DiscoveryEvent[T, K]
case class DiscoverItemRemove[T, K](items: List[K]) extends DiscoveryEvent[T, K]

trait DiscoveryPublisher[F[_], T, K] {
  def update(item: T): F[Unit]
  def remove(itemId: K): F[Unit]
}

trait DiscoverySubscriber[F[_], T, K] {
  def sub(id: String): F[fs2.Stream[F, DiscoveryEvent[T, K]]]
  def unsub(id: String): F[Unit]
}


object DiscoveryCtor {
  def topicBased[F[_], T, K]()(
    implicit F: Concurrent[F]
  ): F[(DiscoveryPublisher[F, T, K], DiscoverySubscriber[F, T, K])] = {
    for {
      topic <- Topic[F, DiscoveryEvent[T, K]](DiscoveryInitial)
      subState <- Ref.of[F, Map[String, SignallingRef[F, Boolean]]](Map.empty)
      sub = new DiscoverySubscriber[F, T, K] {
        override def sub(id: String): F[fs2.Stream[F, DiscoveryEvent[T, K]]] = {
          for {
            stopper <- SignallingRef.apply[F, Boolean](false)
            _ <- subState.update { oldState =>
              oldState ++ Map(id -> stopper)
            }
          } yield topic.subscribe(32).interruptWhen(stopper)
        }

        override def unsub(id: String): F[Unit] = {
          for {
            oldState <- subState.get
            stopper <- F.fromOption(oldState.get(id), DomainError.invalidRequest(s"Can't find model observer $id"))
            _ <- stopper.set(true)
            _ <- subState.update { os =>
              os - id
            }
          } yield ()
        }
      }
      pub = new DiscoveryPublisher[F, T, K] {
        override def update(item: T): F[Unit] = {
          topic.publish1(DiscoverItemUpdate(item :: Nil))
        }

        override def remove(itemId: K): F[Unit] = {
          topic.publish1(DiscoverItemRemove(itemId :: Nil))
        }
      }
    } yield (pub, sub)
  }
}