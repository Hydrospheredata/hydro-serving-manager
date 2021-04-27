package io.hydrosphere.serving.manager.domain.servable

import java.util.concurrent.TimeUnit

import cats.{Applicative, Monad}
import cats.effect.implicits._
import cats.effect._
import cats.implicits._
import io.hydrosphere.serving.manager.domain.application.ApplicationRepository
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.monitoring.{Monitoring, MonitoringRepository}

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.FiniteDuration

// TODO: check fullname
trait ServableGC[F[_]] {
  def mark(modelVersion: ModelVersion.Internal): F[Unit]

  def unmark(modelVersion: ModelVersion.Internal): F[Unit]
}


object ServableGC {

  type GCState = TrieMap[Long, Long]

  def noop[F[_]]()(implicit F: Applicative[F]): ServableGC[F] = {
    new ServableGC[F] {
      override def mark(modelVersion: ModelVersion.Internal): F[Unit] = F.unit

      override def unmark(modelVersion: ModelVersion.Internal): F[Unit] = F.unit
    }
  }

  def empty[F[_]](deletionDelay: FiniteDuration)(
    implicit
    F: Concurrent[F],
    timer: Timer[F],
    appRepo: ApplicationRepository[F],
    servableRepository: ServableRepository[F],
    servService: ServableService[F],
    monitoringRepository: MonitoringRepository[F],
    monitoringService: Monitoring[F]
  ): F[ServableGC[F]] = {
    withState(TrieMap.empty[Long, Long], deletionDelay)
  }

  def withState[F[_]](state: GCState, deletionDelay: FiniteDuration)(
    implicit
    F: Concurrent[F],
    clock: Clock[F],
    appRepo: ApplicationRepository[F],
    servableRepository: ServableRepository[F],
    servService: ServableService[F],
    monitoringRepository: MonitoringRepository[F],
    monitoringService: Monitoring[F]
  ): F[ServableGC[F]] = {
    for {
      _ <- gcLoopStep[F](state, deletionDelay).foreverM[Unit].start
    } yield new ServableGC[F] {
      override def mark(modelVersion: ModelVersion.Internal): F[Unit] = {
        for {
          time <- clock.monotonic(TimeUnit.MILLISECONDS)
        } yield state += (modelVersion.id -> time)
      }

      override def unmark(modelVersion: ModelVersion.Internal): F[Unit] = F.delay {
        state -= modelVersion.id
      }
    }
  }

  def gcLoopStep[F[_]](
    state: GCState,
    deletionDelay: FiniteDuration
  )(implicit
    F: Monad[F],
    clock: Clock[F],
    appRepo: ApplicationRepository[F],
    servService: ServableService[F],
    servableRepository: ServableRepository[F],
    monitoringRepository: MonitoringRepository[F],
    monitoringService: Monitoring[F]
  ): F[Unit] = {
    for {
      currentTime <- clock.monotonic(TimeUnit.MILLISECONDS)
      newState <- state.toList.flatTraverse {
        case (key, timestamp) =>
          if (currentTime >= deletionDelay.toMillis + timestamp) {
            gcModelVersion[F](key).map(x => List(x))
          } else {
            List.empty[Long].pure[F]
          }
      }
    } yield state --= newState
  }

  def gcModelVersion[F[_]](modelVersionId: Long)(
    implicit
    F: Monad[F],
    appRepo: ApplicationRepository[F],
    servService: ServableService[F],
    servableRepository: ServableRepository[F],
    monitoringRepository: MonitoringRepository[F],
    monitoringService: Monitoring[F]
  ): F[Long] = {
    for {
      apps <- appRepo.findVersionUsage(modelVersionId)
      _ <- apps match {
        case Nil => deleteServables[F](modelVersionId)
        case _ => F.unit
      }
    } yield modelVersionId
  }

  def deleteServables[F[_]](modelVersionId: Long)(
    implicit
    F: Monad[F],
    servService: ServableService[F],
    servRepo: ServableRepository[F],
    monitoringRepository: MonitoringRepository[F],
    monitoringService: Monitoring[F]
  ): F[Unit] = {
    for {
      // delete model servables
      servables <- servRepo.findForModelVersion(modelVersionId)
      _ <- servables.traverse(x => servService.stop(x.name))

      // delete monitoring servables
      specs <- monitoringRepository.forModelVersion(modelVersionId)
      monitoringServables = specs.flatMap(_.config.servable)
      _ <- monitoringServables.traverse(x => servService.stop(x.name))

      // remove servables from monitoring specs
      emptySpecs = specs.map { spec =>
        val config = spec.config.copy(servable = None)
        spec.copy(config = config)
      }
      _ <- emptySpecs.traverse(monitoringService.update)

    } yield ()
  }
}