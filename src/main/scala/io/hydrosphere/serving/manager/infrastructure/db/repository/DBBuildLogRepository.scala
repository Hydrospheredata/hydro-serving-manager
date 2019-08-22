package io.hydrosphere.serving.manager.infrastructure.db.repository

import cats.implicits._
import cats.effect.Bracket
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.domain.model_build.BuildLogRepository

object DBBuildLogRepository {
  final case class BuildLogRow(
    version_id: Long,
    logs: List[String]
  )

  def getQ(modelVersionId: Long): doobie.Query0[BuildLogRow] =
    sql"""
         |SELECT * FROM hydro_serving.build_log
         |  WHERE version_id = $modelVersionId
      """.stripMargin.query[BuildLogRow]

  def insertQ(br: BuildLogRow): doobie.Update0 =
    sql"""
         |INSERT INTO hydro_serving.build_log(version_id, logs)
         |  VALUES(${br.version_id}, ${br.logs})
      """.stripMargin.update

  def make[F[_]]()(implicit F: Bracket[F, Throwable], tx: Transactor[F]): BuildLogRepository[F] =
    new BuildLogRepository[F] {
      override def add(modelVersionId: Long, logs: List[String]): F[Unit] = {
        val row = BuildLogRow(modelVersionId, logs)
        insertQ(row).run.transact(tx).void
      }

      override def get(modelVersionId: Long): F[Option[fs2.Stream[F, String]]] = {
        for {
          row <- getQ(modelVersionId).option.transact(tx)
        } yield row.map(x => fs2.Stream.emits(x.logs).covary[F])
      }
    }
}