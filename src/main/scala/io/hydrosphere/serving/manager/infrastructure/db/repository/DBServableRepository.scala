package io.hydrosphere.serving.manager.infrastructure.db.repository

import cats.effect.Bracket
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.domain.servable.Servable.GenericServable
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableRepository}
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBHostSelectorRepository.HostSelectorRow
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBModelRepository.ModelRow
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBModelVersionRepository.ModelVersionRow

object DBServableRepository {
  val tableName = "hydro_serving.servable"

  case class ServableRow(
    service_name: String,
    model_version_id: Long,
    status_text: String,
    host: Option[String],
    port: Option[Int],
    status: String
  )

  def fromServable(s: GenericServable): ServableRow = {
    val (status, statusText, host, port) = s.status match {
      case Servable.Serving(msg, h, p) => ("Serving", msg, h.some, p.some)
      case Servable.NotServing(msg, h, p) => ("NotServing", msg, h, p)
      case Servable.Starting(msg, h, p) => ("Starting", msg, h, p)
      case Servable.NotAvailable(msg, h, p) => ("NotAvailable", msg, h, p)
    }
    ServableRow(
      service_name = s.fullName,
      model_version_id = s.modelVersion.id,
      status_text = statusText,
      host = host,
      port = port,
      status = status
    )
  }

  def toServable(sr: ServableRow, mvr: ModelVersionRow, mr: ModelRow, hsr: Option[HostSelectorRow], apps: List[String]) = {
    val modelVersion = DBModelVersionRepository.toModelVersion(mvr, mr, hsr)
    val suffix = Servable.extractSuffix(mr.name, mvr.model_version, sr.service_name)
    val status = (sr.status, sr.host, sr.port) match {
      case ("Serving", Some(host), Some(port)) => Servable.Serving(sr.status_text, host, port)
      case ("NotServing", host, port) => Servable.NotServing(sr.status_text, host, port)
      case ("NotAvailable", host, port) => Servable.NotAvailable(sr.status_text, host, port)
      case (_, host, port) => Servable.Starting(sr.status_text, host, port)
    }
    Servable(modelVersion, suffix, status, apps)
  }

  def toServableT = (toServable _).tupled

  def allQ =
    sql"""
         |SELECT *, (SELECT array_agg(application_name) AS used_apps FROM hydro_serving.application WHERE service_name = ANY(used_servables)) FROM hydro_serving.servable
         | LEFT JOIN hydro_serving.model_version ON hydro_serving.servable.model_version_id = hydro_serving.model_version.model_version_id
         | LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         | LEFT JOIN hydro_serving.host_selector ON hydro_serving.model_version.host_selector = hydro_serving.host_selector.host_selector_id
         |""".stripMargin.query[(ServableRow, ModelVersionRow, ModelRow, Option[HostSelectorRow], List[String])]

  def upsertQ(sr: ServableRow) =
    sql"""
         |INSERT INTO $tableName(service_name, model_version_id, status_text, host, port, status)
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
         |DELETE FROM $tableName
         | WHERE service_name = $name
      """.stripMargin.update

  def getQ(name: String) =
    sql"""
         |${allQ.sql}
         | WHERE service_name = $name
      """.stripMargin.query[(ServableRow, ModelVersionRow, ModelRow, Option[HostSelectorRow], List[String])]

  def getManyQ(names: Seq[String]) =
    sql"""
         |${allQ.sql}
         | WHERE service_name IN ${names.toList}
      """.stripMargin.query[(ServableRow, ModelVersionRow, ModelRow, Option[HostSelectorRow], List[String])]

  def make[F[_]](tx: Transactor[F])(implicit F: Bracket[F, Throwable]) = new ServableRepository[F] {
    override def all(): F[List[GenericServable]] = {
      for {
        rows <- allQ.to[List].transact(tx)
      } yield rows.map(toServableT)
    }

    override def upsert(servable: GenericServable): F[GenericServable] = {
      val row = fromServable(servable)
      upsertQ(row).run.transact(tx).as(servable)
    }

    override def delete(name: String): F[Int] = {
      deleteQ(name).run.transact(tx)
    }

    override def get(name: String): F[Option[GenericServable]] = {
      for {
        row <- getQ(name).option.transact(tx)
      } yield row.map(toServableT)
    }

    override def get(names: Seq[String]): F[List[GenericServable]] = {
      for {
        rows <- getManyQ(names).to[List].transact(tx)
      } yield rows.map(toServableT)
    }
  }
}