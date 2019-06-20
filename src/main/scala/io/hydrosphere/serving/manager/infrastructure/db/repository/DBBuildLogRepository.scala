package io.hydrosphere.serving.manager.infrastructure.db.repository

import cats.data.OptionT
import cats.implicits._
import cats.effect.Async
import io.hydrosphere.serving.manager.db.Tables
import io.hydrosphere.serving.manager.db.Tables.BuildLogRow
import io.hydrosphere.serving.manager.domain.model_build.BuildLogRepository
import io.hydrosphere.serving.manager.infrastructure.db.DatabaseService

class DBBuildLogRepository[F[_]: Async](implicit databaseService: DatabaseService) extends BuildLogRepository[F] {

  import databaseService._
  import databaseService.driver.api._

  override def add(modelVersionId: Long, logs: List[String]): F[Unit] = {
    db.task(Tables.BuildLog += BuildLogRow(modelVersionId, logs)).void
  }

  override def get(modelVersionId: Long): F[Option[fs2.Stream[F, String]]] = {
    val f = for {
      result <- OptionT(db.task(Tables.BuildLog.filter(_.versionId === modelVersionId).result.headOption))
    } yield fs2.Stream.emits(result.logs).covary[F]
    f.value
  }
}
