package io.hydrosphere.serving.manager.infrastructure.db.repository

import cats.implicits._
import cats.effect.Bracket
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.domain.model_build.BuildLogRepository

object DBBuildLogRepository {
  val tableName = "hydro_serving.build_log"

  val version = 1
  val testQ = sql"SELECT * FROM $tableName hydro_serving.build_log WHERE version_id = $version"

  final case class BuildLogRow(
    version_id: Long,
    logs: List[String]
  )

  def getQ(modelVersionId: Long): doobie.Query0[BuildLogRow] =
    sql"""
         |SELECT * FROM $tableName
         |  WHERE version_id = $modelVersionId
      """.stripMargin.query[BuildLogRow]

  def insertQ(br: BuildLogRow): doobie.Update0 =
    sql"""
         |INSERT INTO $tableName(version_id, logs)
         |  VALUES(${br.version_id}, ${br.logs})
      """.stripMargin.update

  def make[F[_]](tx: Transactor[F])(implicit F: Bracket[F, Throwable]): BuildLogRepository[F] =
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