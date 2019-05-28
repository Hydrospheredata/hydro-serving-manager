package io.hydrosphere.serving.manager.infrastructure.db.repository

import cats.effect.Async
import cats.implicits._
import io.hydrosphere.serving.manager.db.Tables
import io.hydrosphere.serving.manager.domain.servable.Servable.GenericServable
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableRepository}
import io.hydrosphere.serving.manager.infrastructure.db.DatabaseService
import io.hydrosphere.serving.manager.util.AsyncUtil
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.ExecutionContext

class DBServableRepository[F[_]](
  implicit F: Async[F],
  executionContext: ExecutionContext,
  databaseService: DatabaseService,
  modelVersionRepository: DBModelVersionRepository[F]
) extends ServableRepository[F] with Logging {

  import DBServableRepository._
  import databaseService._
  import databaseService.driver.api._

  val joineqQ = Tables.Servable
    .join(modelVersionRepository.joinedQ)

  override def upsert(entity: GenericServable): F[GenericServable] = {
    AsyncUtil.futureAsync {
      val (status, statusText, host, port) = entity.status match {
        case Servable.Serving(msg, h, p) => ("Serving", msg, h.some, p.some)
        case Servable.NotServing(msg, h, p) => ("NotServing", msg, h.some, p.some)
        case Servable.Unknown(msg, h, p) => ("Unknown", msg, h, p)
        case Servable.NotAvailable(msg, h, p) => ("NotAvailable", msg, h, p)
      }

      val row = Tables.ServableRow(
        serviceName = entity.fullName,
        modelVersionId = entity.modelVersion.id,
        statusText = statusText,
        host = host,
        port = port,
        status = status
      )
      db.run(Tables.Servable.insertOrUpdate(row))
    }.as(entity)
  }


  override def delete(name: String): F[Int] = AsyncUtil.futureAsync {
    db.run(Tables.Servable.filter(_.serviceName === name).delete)
  }

  override def all(): F[List[GenericServable]] = {
    for {
      res <- AsyncUtil.futureAsync {
        db.run {
          Tables.Servable
            .join(modelVersionRepository.joinedQ)
            .result
        }
      }
    } yield {
      res.map { case (servable, version) => mapFrom(servable, version) }
        .toList
    }
  }

  override def get(name: String): F[Option[GenericServable]] = {
    for {
      res <- AsyncUtil.futureAsync {
        db.run {
          joineqQ.filter { case (s, _) => s.serviceName === name }
            .result.headOption
        }
      }
    } yield res.map { case (servable, version) => mapFrom(servable, version) }
  }
}

object DBServableRepository {

  def mapFrom(service: Tables.ServableRow, version: (Tables.ModelVersionRow, Tables.ModelRow, Option[Tables.HostSelectorRow])): GenericServable = {

    val mv = DBModelVersionRepository.mapFromDb(version)

    (service.status, service.host, service.port) match {
      case ("Serving", Some(host), Some(port)) => Servable(mv, service.serviceName, Servable.Serving(service.statusText, host, port))
      case ("NotServing", Some(host), Some(port)) => Servable(mv, service.serviceName, Servable.NotServing(service.statusText, host, port))
      case ("NotAvailable", host, port) => Servable(mv, service.serviceName, Servable.NotAvailable(service.statusText, host, port))
      case (_, host, port) => Servable(mv, service.serviceName, Servable.Unknown(service.statusText, host, port))
    }
  }
}