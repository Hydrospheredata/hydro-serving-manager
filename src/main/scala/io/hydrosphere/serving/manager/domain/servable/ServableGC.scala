package io.hydrosphere.serving.manager.domain.servable

import cats.Monad
import cats.data.Chain
import cats.implicits._
import cats.effect.implicits._
import cats.effect.{Concurrent, Timer}
import cats.effect.concurrent.Ref
import io.hydrosphere.serving.manager.domain.application.ApplicationRepository
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionRepository}
import io.hydrosphere.serving.manager.domain.monitoring.MonitoringRepository

trait ServableGC[F[_]] {
  def mark(modelVersion: ModelVersion): F[Unit]
}


object ServableGC {
  def apply[F[_]]()(
    implicit
    F: Concurrent[F],
    timer: Timer[F],
    appRepo: ApplicationRepository[F],
    servableRepository: ServableRepository[F],
    monitoringRepository: MonitoringRepository[F]
  ) = {
    for {
      state <- Ref.of[F, Chain[ModelVersion]](Chain.empty)
//      gcFbr <- gcLoopStep(state).foreverM.start
    } yield new ServableGC[F] {
      override def mark(modelVersion: ModelVersion): F[Unit] = {
        state.update(x => x :+ modelVersion)
      }
    }
  }

  def gcLoopStep[F[_]](
    rState: Ref[F, Chain[ModelVersion]]
  )(implicit
    F: Monad[F],
    appRepo: ApplicationRepository[F],
    servableRepository: ServableRepository[F],
    monitoringRepository: MonitoringRepository[F]
  ) = {
    for {
      state <- rState.get
//      newStateDiff <- state.traverse(gcModelVersion)
//      newState = newStateDiff.flatten
//      _ <- rState.update(x => x.filterNot(mv => newState.contains(mv)))
    } yield ()
  }

  def gcModelVersion[F[_]](modelVersion: ModelVersion)(
    implicit
    F: Monad[F],
    appRepo: ApplicationRepository[F],
    servableRepository: ServableRepository[F],
    monitoringRepository: MonitoringRepository[F]
  ): F[Option[ModelVersion]] = ???
}