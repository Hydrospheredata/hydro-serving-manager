package io.hydrosphere.serving.manager.discovery.application

import cats.effect.concurrent.{Ref, Semaphore}
import cats.effect.{Concurrent, Sync}
import cats.implicits._
import io.hydrosphere.serving.manager.grpc.entities.ServingApp

trait ApplicationDiscoveryHub[F[_]] {
  def update(e: ApplicationEvent): F[Unit]
  def added(app: ServingApp): F[Unit] = update(ApplicationEvent.Started(app))
  def removed(id: Long): F[Unit] = update(ApplicationEvent.Removed(id))
  def current: F[List[ServingApp]]
}

trait ObservedApplicationDiscoveryHub[F[_]] extends ApplicationDiscoveryHub[F] {
  def register(id: String, obs: AppsObserver[F]): F[Unit]
  def unregister(id: String): F[Unit]
}

object ApplicationDiscoveryHub {

  private case class State[F[_]](
    apps: Map[String, ServingApp],
    observers: Map[String, AppsObserver[F]]
  )
  private object State {
    def empty[F[_]]: State[F] = State[F](Map.empty, Map.empty)
  }

  def observed[F[_]](implicit F: Concurrent[F]): F[ObservedApplicationDiscoveryHub[F]] = {
    for {
      stateRef <- Ref.of[F, State[F]](State.empty)
      semaphore <- Semaphore[F](1)
    } yield observed(stateRef, semaphore)
  }

  def observed[F[_]](
    ref: Ref[F, State[F]],
    semaphore: Semaphore[F],
  )(implicit F: Sync[F]): ObservedApplicationDiscoveryHub[F] = {
    new ObservedApplicationDiscoveryHub[F] {
      
      private def useLock[A](f: => F[A]): F[A] = F.bracket(semaphore.acquire)(_ => f)(_ => semaphore.release)
      
      override def register(id: String, obs: AppsObserver[F]): F[Unit] = {
        val op = for {
          st <- ref.get
          _  <- ref.update(st => st.copy(observers = st.observers + (id -> obs)))
          _  <- obs.apps(st.apps.values.toList)
        } yield ()
        useLock(op)
      }
  
      override def unregister(id: String): F[Unit] = {
        useLock(ref.update(st => st.copy(observers = st.observers - id)))
      }
  
      override def update(e: ApplicationEvent): F[Unit] = {
        val notify = e match {
          case ApplicationEvent.Removed(id) => (o: AppsObserver[F]) => o.removed(List(id))
          case ApplicationEvent.Started(app) => (o: AppsObserver[F]) => o.added(app)
        }
        
        val update = e match {
          case ApplicationEvent.Removed(id) =>
            (st: State[F]) => {
              val filtered = st.apps.filter(_._1 != id.toString)
              st.copy(apps = filtered)
            }
          case ApplicationEvent.Started(app) =>
            (st: State[F]) => {
              val added = st.apps + (app.id -> app)
              st.copy(apps = added)
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
  
      override def current: F[List[ServingApp]] = ref.get.map(_.apps.values.toList)
    }
  }
  
}

