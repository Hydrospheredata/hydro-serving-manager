package io.hydrosphere.serving.manager.discovery.servable

import cats.implicits._
import cats.effect.{Concurrent, Sync}
import cats.effect.concurrent.{Ref, Semaphore}
import io.hydrosphere.serving.discovery.serving.ServableDiscoveryEvent
import io.hydrosphere.serving.manager.discovery.servable.ServableDiscoveryHub.{ServableAdded, ServableEvent, ServableRemoved}
import io.hydrosphere.serving.manager.grpc.entities.Servable

trait ServableDiscoveryHub[F[_]] {
  def update(ev: ServableEvent): F[Unit]
  def removed(names: List[String]): F[Unit] = update(ServableRemoved(names))
  def removedSignle(names: String): F[Unit] = update(ServableRemoved(List(names)))
  def added(servable: List[Servable]): F[Unit] = update(ServableAdded(servable))
  def addedSingle(servable: Servable): F[Unit] = update(ServableAdded(List(servable)))
  def current: F[List[Servable]]
}

trait ObservedServableDiscoveryHub[F[_]] extends ServableDiscoveryHub[F] {
  def register(id: String, obs: ServableObserver[F]): F[Unit]
  def unregister(id: String): F[Unit]
}

object ServableDiscoveryHub {

  sealed trait ServableEvent
  final case class ServableAdded(s: List[Servable]) extends ServableEvent
  final case class ServableRemoved(s: List[String]) extends ServableEvent

  private case class State[F[_]](
    servables: Map[String, Servable],
    observers: Map[String, ServableObserver[F]]
  )
  private object State {
    def empty[F[_]]: State[F] = State[F](Map.empty, Map.empty)
  }

  def observed[F[_]](implicit F: Concurrent[F]): F[ObservedServableDiscoveryHub[F]] = {
    for {
      stateRef <- Ref.of[F, State[F]](State.empty)
      semaphore <- Semaphore[F](1)
    } yield observed(stateRef, semaphore)
  }

  def observed[F[_]](
    ref: Ref[F, State[F]],
    semaphore: Semaphore[F],
  )(implicit F: Sync[F]): ObservedServableDiscoveryHub[F] = {
    new ObservedServableDiscoveryHub[F] {

      private def useLock[A](f: => F[A]): F[A] = F.bracket(semaphore.acquire)(_ => f)(_ => semaphore.release)

      override def register(id: String, obs: ServableObserver[F]): F[Unit] = {
        val op = for {
          st <- ref.get
          _  <- ref.update(st => st.copy(observers = st.observers + (id -> obs)))
          _  <- added(st.servables.values.toList)
        } yield ()
        useLock(op)
      }

      override def unregister(id: String): F[Unit] = {
        useLock(ref.update(st => st.copy(observers = st.observers - id)))
      }

      override def update(e: ServableEvent): F[Unit] = {
        val notify = e match {
          case ServableRemoved(id) => (o: ServableObserver[F]) =>
            o.notify(ServableDiscoveryEvent(removedIdx = id))
          case ServableAdded(s) => (o: ServableObserver[F]) =>
            o.notify(ServableDiscoveryEvent(added = s))
        }

        val update = e match {
          case ServableRemoved(id) =>
            (st: State[F]) => {
              val filtered = st.servables.filterNot(kv => id.contains(kv._1))
              st.copy(servables = filtered)
            }
          case ServableAdded(app) =>
            (st: State[F]) => {
              val added = st.servables ++ app.map(x => x.name -> x).toMap
              st.copy(servables = added)
            }
        }

        val op = for {
          state <- ref.modify(st => {
            val next = update(st)
            (next, next)
          })
          _  <- state.observers.values.toList.traverse(o => notify(o))
        } yield ()

        useLock(op)
      }

      override def current: F[List[Servable]] = ref.get.map(_.servables.values.toList)
    }
  }
}