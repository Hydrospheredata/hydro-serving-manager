package io.hydrosphere.serving.manager.infrastructure.db.repository

import cats.effect.Bracket
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableEvents, ServableRepository}
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBModelRepository.ModelRow
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBModelVersionRepository.ModelVersionRow
import io.hydrosphere.serving.manager.util.CollectionOps._
import cats.data.NonEmptyList
import cats.data.OptionT
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfiguration
import DBDeploymentConfigurationRepository._
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.util.JsonOps._

import io.circe.syntax._
import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.infrastructure.db.Metas._

object DBServableRepository {

  @JsonCodec
  case class ServableRow(
      service_name: String,
      model_version_id: Long,
      status_text: Option[String],
      host: Option[String],
      port: Option[Int],
      status: String,
      metadata: Option[String],
      deployment_configuration: Option[String]
  )

  type JoinedServableRow =
    (ServableRow, ModelVersionRow, ModelRow, Option[DeploymentConfiguration], Option[List[String]])

  def fromServable(s: Servable, defaultDCName: String): ServableRow = {
    val depConf =
      if (s.deploymentConfiguration.name == defaultDCName)
        None
      else s.deploymentConfiguration.name.some
    ServableRow(
      service_name = s.name,
      model_version_id = s.modelVersion.id,
      status_text = s.message,
      host = s.host,
      port = s.port,
      status = s.status.entryName,
      metadata = s.metadata.maybeEmpty.map(_.asJson.noSpaces),
      deployment_configuration = depConf
    )
  }

  def toServable(
      sr: ServableRow,
      mvr: ModelVersionRow,
      mr: ModelRow,
      deploymentConfig: Option[DeploymentConfiguration],
      apps: Option[List[String]],
      defaultDC: DeploymentConfiguration
  ) = {

    val suffix = Servable.extractSuffix(mr.name, mvr.model_version, sr.service_name)
    val status =
      Servable.Status.withNameInsensitiveOption(sr.status).getOrElse(Servable.Status.NotAvailable)

    DBModelVersionRepository
      .toModelVersion(mvr, mr)
      .flatMap(mvr =>
        mvr match {
          case imv: ModelVersion.Internal =>
            Right(
              Servable(
                modelVersion = imv,
                name = sr.service_name,
                status = status,
                usedApps = apps.getOrElse(Nil),
                metadata = sr.metadata
                  .flatMap(m => m.parseJsonAs[Map[String, String]].toOption)
                  .getOrElse(Map.empty),
                message = sr.status_text,
                host = sr.host,
                port = sr.port,
                deploymentConfiguration = deploymentConfig.getOrElse(defaultDC)
              )
            )
          case emv: ModelVersion.External =>
            Left(
              DomainError.internalError(
                s"Impossible Servable ${sr.service_name} with external ModelVersion: ${emv}"
              )
            )
        }
      )
  }

  def allQ =
    sql"""
         |SELECT *, (SELECT array_agg(application_name) AS used_apps FROM hydro_serving.application WHERE service_name = ANY(used_servables)) FROM hydro_serving.servable
         | LEFT JOIN hydro_serving.model_version ON hydro_serving.servable.model_version_id = hydro_serving.model_version.model_version_id
         | LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         | LEFT JOIN hydro_serving.deployment_configuration ON hydro_serving.servable.deployment_configuration = hydro_serving.deployment_configuration.name
    """.stripMargin.query[JoinedServableRow]

  def upsertQ(sr: ServableRow) =
    sql"""
         |INSERT INTO hydro_serving.servable(service_name, model_version_id, status_text, host, port, status, deployment_configuration, metadata)
         | VALUES(${sr.service_name}, ${sr.model_version_id}, ${sr.status_text}, ${sr.host}, ${sr.port}, ${sr.status}, ${sr.deployment_configuration}, ${sr.metadata})
         | ON CONFLICT (service_name)
         |  DO UPDATE
         |   SET service_name = ${sr.service_name},
         |       model_version_id = ${sr.model_version_id},
         |       status_text = ${sr.status_text},
         |       host = ${sr.host},
         |       port = ${sr.port},
         |       status = ${sr.status},
         |       deployment_configuration = ${sr.deployment_configuration},
         |       metadata = ${sr.metadata}
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
         | LEFT JOIN hydro_serving.deployment_configuration ON hydro_serving.servable.deployment_configuration = hydro_serving.deployment_configuration.name
         |   WHERE service_name = $name
      """.stripMargin.query[JoinedServableRow]

  def getManyQ(names: NonEmptyList[String]) = {
    val frag =
      fr"""
          |SELECT *, (SELECT array_agg(application_name) AS used_apps FROM hydro_serving.application WHERE service_name = ANY(used_servables)) FROM hydro_serving.servable
          | LEFT JOIN hydro_serving.model_version ON hydro_serving.servable.model_version_id = hydro_serving.model_version.model_version_id
          | LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
          | LEFT JOIN hydro_serving.deployment_configuration ON hydro_serving.servable.deployment_configuration = hydro_serving.deployment_configuration.name
          |  WHERE """.stripMargin ++ Fragments.in(fr"service_name", names)
    frag.query[JoinedServableRow]
  }

  def findForModelVersionQ(versionId: Long) =
    sql"""
         |SELECT *, (SELECT array_agg(application_name) AS used_apps FROM hydro_serving.application WHERE service_name = ANY(used_servables)) FROM hydro_serving.servable
         | LEFT JOIN hydro_serving.model_version ON hydro_serving.servable.model_version_id = hydro_serving.model_version.model_version_id
         | LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         | LEFT JOIN hydro_serving.deployment_configuration ON hydro_serving.servable.deployment_configuration = hydro_serving.deployment_configuration.name
         |   WHERE hydro_serving.servable.model_version_id = $versionId
      """.stripMargin.query[JoinedServableRow]

  def make[F[_]](defaultDC: DeploymentConfiguration)(implicit
      F: Bracket[F, Throwable],
      tx: Transactor[F],
      servablePub: ServableEvents.Publisher[F]
  ) =
    new ServableRepository[F] {
      override def findForModelVersion(versionId: Long): F[List[Servable]] =
        for {
          rows <- findForModelVersionQ(versionId).to[List].transact(tx)
        } yield rows
          .map(x => toServable(x._1, x._2, x._3, x._4, x._5, defaultDC).toOption)
          .collect {
            case Some(x) => x
          }

      override def all(): F[List[Servable]] =
        for {
          rows <- allQ.to[List].transact(tx)
        } yield rows
          .map(x => toServable(x._1, x._2, x._3, x._4, x._5, defaultDC).toOption)
          .collect {
            case Some(x) => x
          }

      override def upsert(servable: Servable): F[Servable] = {
        val row = fromServable(servable, defaultDC.name)
        upsertQ(row).run
          .transact(tx)
          .as(servable)
          .flatTap(servablePub.update)
      }

      override def delete(name: String): F[Int] =
        deleteQ(name).run
          .transact(tx)
          .flatTap(_ => servablePub.remove(name))

      override def get(name: String): F[Option[Servable]] =
        for {
          row <- getQ(name).option.transact(tx)
        } yield row.flatMap(x => toServable(x._1, x._2, x._3, x._4, x._5, defaultDC).toOption)

      override def get(names: Seq[String]): F[List[Servable]] = {
        val okCase = for {
          nonEmptyNames <- OptionT.fromOption[F](NonEmptyList.fromList(names.toList))
          rows          <- OptionT.liftF(getManyQ(nonEmptyNames).to[List].transact(tx))
        } yield rows
          .map(x => toServable(x._1, x._2, x._3, x._4, x._5, defaultDC).toOption)
          .collect {
            case Some(x) => x
          }
        okCase.getOrElse(Nil)
      }
    }
}
