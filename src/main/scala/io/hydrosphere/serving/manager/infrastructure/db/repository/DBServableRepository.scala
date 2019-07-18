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
  modelVersionRepository: DBModelVersionRepository[F],
) extends ServableRepository[F] with Logging {

  import DBServableRepository._
  import databaseService._
  import databaseService.driver.api._

  /*
SELECT *, 	
(SELECT array_agg(application_name) AS used_apps FROM hydro_serving.application WHERE service_name = ANY(used_servables))
 FROM hydro_serving.servable
  */
  
  val joineqQ =
    sql"SELECT *, (SELECT array_agg(application_name) AS used_apps FROM hydro_serving.application WHERE service_name = ANY(used_servables)) FROM hydro_serving.servable"
      .as[(String, Long, String, Option[String], Option[Int], String, List[String])]
    .joinLeft(modelVersionRepository.joinedQ)
    .on { case ((s, _), (m, _, _)) =>
      s.modelVersionId === m.modelVersionId
    }

  override def upsert(entity: GenericServable): F[GenericServable] = {
    AsyncUtil.futureAsync {
      logger.debug(s"upsert $entity")
      val (status, statusText, host, port) = entity.status match {
        case Servable.Serving(msg, h, p) => ("Serving", msg, h.some, p.some)
        case Servable.NotServing(msg, h, p) => ("NotServing", msg, h, p)
        case Servable.Starting(msg, h, p) => ("Starting", msg, h, p)
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
      val q = Tables.Servable.insertOrUpdate(row)
      db.run(q)
    }.as(entity)
  }


  override def delete(name: String): F[Int] = AsyncUtil.futureAsync {
    db.run(Tables.Servable.filter(_.serviceName === name).delete)
  }

  override def all(): F[List[GenericServable]] = {
    for {
      res <- AsyncUtil.futureAsync {
        db.run(joineqQ.result)
      }
    } yield {
      res.flatMap {
        case ((servable, apps), Some(version)) => mapFrom(servable, apps, version) :: Nil
        case ((x, _), _) =>
          logger.error(s"Trying to get servable ${x.serviceName} but it doesn't have ModelVersion")
          Nil
      }.toList
    }
  }

  override def get(name: String): F[Option[GenericServable]] = {
    for {
      res <- AsyncUtil.futureAsync {
        logger.debug(s"get $name")
        db.run {
          joineqQ.filter { case ((s, _), _) => s.serviceName === name }.result.headOption
        }
      }
    } yield res.flatMap {
      case ((servable, apps), Some(version)) => mapFrom(servable, apps, version).some
      case _ => None
    }
  }

  override def get(names: Seq[String]): F[List[GenericServable]] = {
    for {
      res <- AsyncUtil.futureAsync {
        logger.debug(s"get $names")
        db.run {
          joineqQ.filter { case ((s, _), _) => s.serviceName inSetBind names }
            .result
        }
      }
      servables = res.toList.flatMap {
        case ((servable, apps), Some(version)) => mapFrom(servable, apps, version) :: Nil
        case _ => Nil
      }
    } yield servables
  }
}

object DBServableRepository {

  def mapFrom(service: Tables.ServableRow, apps: List[String], version: (Tables.ModelVersionRow, Tables.ModelRow, Option[Tables.HostSelectorRow])): GenericServable = {

    val mv = DBModelVersionRepository.mapFromDb(version)
    val suffix = Servable.extractSuffix(version._2.name, version._1.modelVersion, service.serviceName)

    val status = (service.status, service.host, service.port) match {
      case ("Serving", Some(host), Some(port)) => Servable.Serving(service.statusText, host, port)
      case ("NotServing", host, port) => Servable.NotServing(service.statusText, host, port)
      case ("NotAvailable", host, port) => Servable.NotAvailable(service.statusText, host, port)
      case (_, host, port) => Servable.Starting(service.statusText, host, port)
    }
    Servable(mv, suffix, status, apps)
  }
}