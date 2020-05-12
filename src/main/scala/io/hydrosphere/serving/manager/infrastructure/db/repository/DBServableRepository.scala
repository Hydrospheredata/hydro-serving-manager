package io.hydrosphere.serving.manager.infrastructure.db.repository

import cats.data.{NonEmptyList, NonEmptySet}
import cats.effect.Bracket
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.implicits.javatime._
import doobie.postgres.implicits._
import io.hydrosphere.serving.manager.infrastructure.db.Metas._
import doobie.util.transactor.Transactor
import io.circe.parser._
import io.circe.syntax._
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableRepository}
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBHostSelectorRepository.HostSelectorRow
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBModelRepository.ModelRow
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBModelVersionRepository.ModelVersionRow
import io.hydrosphere.serving.manager.util.CollectionOps._
import DBServableRepository._

class DBServableRepository[F[_]](tx: Transactor[F])(implicit F: Bracket[F, Throwable])
    extends ServableRepository[F] {
  override def findForModelVersion(versionId: Long): F[List[Servable]] =
    for {
      rows   <- findForModelVersionQ(versionId).to[List].transact(tx)
      mapped <- F.fromEither(rows.traverse(toServableT))
    } yield mapped

  override def all(): F[List[Servable]] =
    for {
      rows   <- allQ.to[List].transact(tx)
      mapped <- F.fromEither(rows.traverse(toServableT))
    } yield mapped

  override def upsert(servable: Servable): F[Servable] = {
    val row = fromServable(servable)
    upsertQ(row).run.transact(tx).as(servable)
  }

  override def delete(name: String): F[Int] =
    deleteQ(name).run.transact(tx)

  override def get(name: String): F[Option[Servable]] =
    for {
      row    <- getQ(name).option.transact(tx)
      mapped <- F.fromEither(row.traverse(toServableT))
    } yield mapped

  override def get(names: NonEmptySet[String]): F[List[Servable]] =
    for {
      rows   <- getManyQ(names.toNonEmptyList).to[List].transact(tx)
      mapped <- F.fromEither(rows.traverse(toServableT))
    } yield mapped
}

object DBServableRepository {

  final case class ServableRow(
      service_name: String,
      model_version_id: Long,
      status_text: String,
      host: Option[String],
      port: Option[Int],
      status: String,
      metadata: Option[String]
  )

  type JoinedServableRow =
    (ServableRow, ModelVersionRow, ModelRow, Option[HostSelectorRow], Option[List[String]])

  def fromServable(s: Servable): ServableRow =
    ServableRow(
      service_name = s.fullName,
      model_version_id = s.modelVersion.id,
      status_text = s.status.entryName,
      host = s.host,
      port = s.port,
      status = s.message,
      metadata = s.metadata.maybeEmpty.map(_.asJson.noSpaces)
    )

  def toServable(
      sr: ServableRow,
      mvr: ModelVersionRow,
      mr: ModelRow,
      hsr: Option[HostSelectorRow],
      apps: Option[List[String]]
  ): Either[Throwable, Servable] = {
    val suffix = Servable.extractSuffix(mr.name, mvr.model_version, sr.service_name)

    val status =
      Servable.Status.withNameInsensitiveOption(sr.status).getOrElse(Servable.Status.NotAvailable)
    DBModelVersionRepository.toModelVersion(mvr, mr, hsr) match {
      case imv: ModelVersion.Internal =>
        Right(
          Servable(
            modelVersion = imv,
            nameSuffix = suffix,
            status = status,
            usedApps = apps.getOrElse(Nil),
            metadata = sr.metadata
              .flatMap(m => parse(m).flatMap(_.as[Map[String, String]]).toOption)
              .getOrElse(Map.empty),
            message = sr.status_text,
            host = sr.host,
            port = sr.port
          )
        )
      case emv: ModelVersion.External =>
        Left(
          DomainError.internalError(
            s"Impossible Servable ${sr.service_name} with external ModelVersion: ${emv}"
          )
        )
    }
  }

  def toServableT = (toServable _).tupled

  def allQ =
    sql"""
         |SELECT *, (SELECT array_agg(application_name) AS used_apps FROM hydro_serving.application WHERE service_name = ANY(used_servables)) FROM hydro_serving.servable
         | LEFT JOIN hydro_serving.model_version ON hydro_serving.servable.model_version_id = hydro_serving.model_version.model_version_id
         | LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         | LEFT JOIN hydro_serving.host_selector ON hydro_serving.model_version.host_selector = hydro_serving.host_selector.host_selector_id
         |""".stripMargin.query[JoinedServableRow]

  def upsertQ(sr: ServableRow) =
    sql"""
         |INSERT INTO hydro_serving.servable(service_name, model_version_id, status_text, host, port, status)
         | VALUES(${sr.service_name}, ${sr.model_version_id}, ${sr.status_text}, ${sr.host}, ${sr.port}, ${sr.status})
         | ON CONFLICT (service_name)
         |  DO UPDATE
         |   SET service_name = ${sr.service_name},
         |       model_version_id = ${sr.model_version_id},
         |       status_text = ${sr.status_text},
         |       host = ${sr.host},
         |       port = ${sr.port},
         |       status = ${sr.status}
      """.stripMargin.update

  def deleteQ(name: String) =
    sql"""
         |DELETE FROM hydro_serving.servable
         | WHERE service_name = $name
      """.stripMargin.update

  def getQ(name: String) =
    sql"""
         |SELECT *, (SELECT array_agg(application_name) AS used_apps FROM hydro_serving.application WHERE service_name = ANY(used_servables)) FROM hydro_serving.servable
         | LEFT JOIN hydro_serving.model_version ON hydro_serving.servable.model_version_id = hydro_serving.model_version.model_version_id
         | LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         | LEFT JOIN hydro_serving.host_selector ON hydro_serving.model_version.host_selector = hydro_serving.host_selector.host_selector_id
         |   WHERE service_name = $name
      """.stripMargin.query[JoinedServableRow]

  def getManyQ(names: NonEmptyList[String]) = {
    val frag =
      fr"""
          |SELECT *, (SELECT array_agg(application_name) AS used_apps FROM hydro_serving.application WHERE service_name = ANY(used_servables)) FROM hydro_serving.servable
          | LEFT JOIN hydro_serving.model_version ON hydro_serving.servable.model_version_id = hydro_serving.model_version.model_version_id
          | LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
          | LEFT JOIN hydro_serving.host_selector ON hydro_serving.model_version.host_selector = hydro_serving.host_selector.host_selector_id
          |  WHERE """.stripMargin ++ Fragments.in(fr"service_name", names)
    frag.query[JoinedServableRow]
  }

  def findForModelVersionQ(versionId: Long) =
    sql"""
         |SELECT *, (SELECT array_agg(application_name) AS used_apps FROM hydro_serving.application WHERE service_name = ANY(used_servables)) FROM hydro_serving.servable
         | LEFT JOIN hydro_serving.model_version ON hydro_serving.servable.model_version_id = hydro_serving.model_version.model_version_id
         | LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         | LEFT JOIN hydro_serving.host_selector ON hydro_serving.model_version.host_selector = hydro_serving.host_selector.host_selector_id
         |   WHERE hydro_serving.servable.model_version_id = $versionId
      """.stripMargin.query[JoinedServableRow]
}
