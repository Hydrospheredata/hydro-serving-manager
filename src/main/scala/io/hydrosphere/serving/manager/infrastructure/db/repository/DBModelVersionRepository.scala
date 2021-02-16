package io.hydrosphere.serving.manager.infrastructure.db.repository

import java.time.Instant

import cats.effect.Bracket
import cats.implicits._
import cats.data.NonEmptyList

import doobie._
import doobie.implicits._
import doobie.implicits.javatime._
import doobie.implicits.javasql._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.domain.monitoring.MonitoringConfiguration
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.{
  ModelVersion,
  ModelVersionEvents,
  ModelVersionRepository,
  ModelVersionStatus
}
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBModelRepository.ModelRow
import io.hydrosphere.serving.manager.infrastructure.db.Metas._
import io.hydrosphere.serving.manager.domain.contract.{DataType, Field, Signature, TensorShape}

import io.circe.parser._
import io.circe.syntax._
import io.circe.Json
import io.circe.generic.JsonCodec

object DBModelVersionRepository {
  @JsonCodec
  final case class ModelVersionRow(
      model_version_id: Long,
      model_id: Long,
      created_timestamp: Instant,
      finished_timestamp: Option[Instant],
      model_version: Long,
      model_signature: Json,
      image_name: String,
      image_tag: String,
      image_sha256: Option[String],
      runtime_name: String,
      runtime_version: String,
      status: String,
      profile_types: Option[String],
      install_command: Option[String],
      metadata: Option[String],
      is_external: Boolean,
      monitoring_configuration: Json
  )

  final case class ModelVersionRow2(
      model_version_id: Long,
      model_id: Long,
      finished_timestamp: Option[Instant],
      model_version: Long,
      model_contract: Json,
      image_name: String,
      image_tag: String,
      image_sha256: Option[String],
      runtime_name: String,
      runtime_version: String,
      status: String,
      profile_types: Option[String],
      install_command: Option[String],
      metadata: Option[String],
      is_external: Boolean,
      monitoring_configuration: Json
  )

  Read[ModelVersionRow2]
  type JoinedModelVersionRow = (ModelVersionRow, ModelRow)

  def toModelVersion(mvr: ModelVersionRow, mr: ModelRow): Either[Throwable, ModelVersion] = {
    val image = DockerImage(
      name = mvr.image_name,
      tag = mvr.image_tag,
      sha256 = mvr.image_sha256
    )
    val model = Model(
      id = mr.model_id,
      name = mr.name
    )
    val runtime = DockerImage(
      name = mvr.runtime_name,
      tag = mvr.runtime_version
    )

    for {
      monitoringConfig <- mvr.monitoring_configuration.as[MonitoringConfiguration]
      metadata =
        mvr.metadata
          .flatMap(str => parse(str).flatMap(_.as[Map[String, String]]).toOption)
          .getOrElse(Map.empty)
      signature <- mvr.model_signature.as[Signature]
    } yield
      if (mvr.is_external)
        ModelVersion.External(
          id = mvr.model_version_id,
          created = mvr.created_timestamp,
          modelVersion = mvr.model_version,
          modelSignature = signature,
          model = model,
          metadata = metadata,
          monitoringConfiguration = monitoringConfig
        )
      else
        ModelVersion.Internal(
          id = mvr.model_version_id,
          image = image,
          created = mvr.created_timestamp,
          finished = mvr.finished_timestamp,
          modelVersion = mvr.model_version,
          modelSignature = signature,
          runtime = runtime,
          model = model,
          status = ModelVersionStatus.withName(mvr.status),
          installCommand = mvr.install_command,
          metadata = metadata,
          monitoringConfiguration = monitoringConfig
        )
  }

  def fromModelVersion(amv: ModelVersion): ModelVersionRow = {
    val metadata =
      if (amv.metadata.nonEmpty)
        amv.metadata.asJson.noSpaces.some
      else None

    amv match {
      case mv: ModelVersion.Internal =>
        ModelVersionRow(
          model_version_id = mv.id,
          model_id = mv.model.id,
          created_timestamp = mv.created,
          finished_timestamp = mv.finished,
          model_version = mv.modelVersion,
          model_signature = mv.modelSignature.asJson,
          image_name = mv.image.name,
          image_tag = mv.image.tag,
          image_sha256 = mv.image.sha256,
          runtime_name = mv.runtime.name,
          runtime_version = mv.runtime.tag,
          status = mv.status.toString,
          profile_types = None,
          install_command = mv.installCommand,
          metadata = metadata,
          is_external = false,
          monitoring_configuration = mv.monitoringConfiguration.asJson
        )
      case emv: ModelVersion.External =>
        ModelVersionRow(
          model_version_id = emv.id,
          model_id = emv.model.id,
          created_timestamp = emv.created,
          finished_timestamp = Some(emv.created),
          model_version = emv.modelVersion,
          model_signature = emv.modelSignature.asJson,
          image_name = DockerImage.dummyImage.name,
          image_tag = DockerImage.dummyImage.tag,
          image_sha256 = None,
          runtime_name = DockerImage.dummyImage.name,
          runtime_version = DockerImage.dummyImage.tag,
          status = ModelVersionStatus.Released.toString,
          profile_types = None,
          install_command = None,
          metadata = metadata,
          is_external = true,
          monitoring_configuration = emv.monitoringConfiguration.asJson
        )
    }
  }

  def toModelVersionT = (toModelVersion _).tupled

  def allQ: doobie.Query0[JoinedModelVersionRow] =
    sql"""
         |SELECT * FROM hydro_serving.model_version
         |  LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         |""".stripMargin.query[JoinedModelVersionRow]

  def getQ(id: Long): doobie.Query0[JoinedModelVersionRow] =
    sql"""
         |SELECT * FROM hydro_serving.model_version
         |  LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         |  WHERE hydro_serving.model_version.model_version_id = $id
         |""".stripMargin.query[JoinedModelVersionRow]

  def getQ(name: String, version: Long): doobie.Query0[JoinedModelVersionRow] =
    sql"""
         |SELECT * FROM hydro_serving.model_version
         |  LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         |  WHERE hydro_serving.model.name = $name AND model_version = $version
         |""".stripMargin.query[JoinedModelVersionRow]

  def listVersionsQ(modelId: Long): doobie.Query0[JoinedModelVersionRow] =
    sql"""
         |SELECT * FROM hydro_serving.model_version
         |  LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         |  WHERE hydro_serving.model_version.model_id = $modelId
         |""".stripMargin.query[JoinedModelVersionRow]

  def findVersionsQ(versionIdx: NonEmptyList[Long]): doobie.Query0[JoinedModelVersionRow] = {
    val q =
      fr"""
          |SELECT * FROM hydro_serving.model_version
          | LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
          |  WHERE """.stripMargin
    val fullQ = q ++ Fragments.in(fr"hydro_serving.model_version.model_version_id", versionIdx)
    fullQ.query[JoinedModelVersionRow]
  }

  def lastModelVersionQ(modelId: Long): doobie.Query0[JoinedModelVersionRow] =
    sql"""
         |SELECT * FROM hydro_serving.model_version
         |  LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         |  WHERE hydro_serving.model_version.model_id = $modelId
         |  ORDER BY hydro_serving.model_version.model_version DESC
         |  LIMIT 1
         |""".stripMargin.query[JoinedModelVersionRow]

  def insertQ(mv: ModelVersionRow): doobie.Update0 =
    sql"""
         |INSERT INTO hydro_serving.model_version (
         | model_id,
         | created_timestamp,
         | finished_timestamp,
         | model_version,
         | model_contract,
         | image_name,
         | image_tag,
         | image_sha256,
         | runtime_name,
         | runtime_version,
         | status,
         | install_command,
         | metadata,
         | is_external,
         | monitoring_configuration
         | ) VALUES (
         | ${mv.model_id},
         | ${mv.created_timestamp},
         | ${mv.finished_timestamp},
         | ${mv.model_version},
         | ${mv.model_signature},
         | ${mv.image_name},
         | ${mv.image_tag},
         | ${mv.image_sha256},
         | ${mv.runtime_name},
         | ${mv.runtime_version},
         | ${mv.status},
         | ${mv.install_command},
         | ${mv.metadata},
         | ${mv.is_external},
         | ${mv.monitoring_configuration}
         |)""".stripMargin.update

  def updateQ(mv: ModelVersionRow): doobie.Update0 =
    sql"""
         |UPDATE hydro_serving.model_version SET
         | model_id = ${mv.model_id},
         | created_timestamp = ${mv.created_timestamp},
         | finished_timestamp = ${mv.finished_timestamp},
         | model_version = ${mv.model_version},
         | model_contract = ${mv.model_signature},
         | image_name = ${mv.image_name},
         | image_tag = ${mv.image_tag},
         | image_sha256 = ${mv.image_sha256},
         | runtime_name = ${mv.runtime_name},
         | runtime_version = ${mv.runtime_version},
         | status = ${mv.status},
         | install_command = ${mv.install_command},
         | metadata = ${mv.metadata},
         | monitoring_configuration = ${mv.monitoring_configuration}
         | WHERE model_version_id = ${mv.model_version_id}""".stripMargin.update

  def deleteQ(id: Long): doobie.Update0 =
    sql"""
         |DELETE FROM hydro_serving.model_version
         |  WHERE model_version_id = $id
      """.stripMargin.update

  def make[F[_]]()(implicit
      F: Bracket[F, Throwable],
      tx: Transactor[F],
      modelPub: ModelVersionEvents.Publisher[F]
  ): ModelVersionRepository[F] =
    new ModelVersionRepository[F] {
      override def all(): F[List[ModelVersion]] =
        for {
          rows <- allQ.to[List].transact(tx)
        } yield rows.map(x =>
          toModelVersion(x._1, x._2).toOption match {
            case Some(value) => value
          }
        )

      override def create(entity: ModelVersion): F[ModelVersion] =
        insertQ(fromModelVersion(entity))
          .withUniqueGeneratedKeys[Long]("model_version_id")
          .transact(tx)
          .map { id =>
            entity match {
              case imv: ModelVersion.Internal => imv.copy(id = id)
              case emv: ModelVersion.External => emv.copy(id = id)
            }
          }
          .flatTap(modelPub.update)

      override def update(entity: ModelVersion): F[Int] =
        updateQ(fromModelVersion(entity)).run
          .transact(tx)
          .flatTap(_ => modelPub.update(entity))

      override def get(id: Long): F[Option[ModelVersion]] =
        for {
          row <- getQ(id).option.transact(tx)
        } yield row.flatMap(toModelVersionT(_).toOption)

      override def get(modelName: String, modelVersion: Long): F[Option[ModelVersion]] =
        for {
          row <- getQ(modelName, modelVersion).option.transact(tx)
        } yield row.flatMap(toModelVersionT(_).toOption)

      override def delete(id: Long): F[Int] =
        deleteQ(id).run
          .transact(tx)
          .flatTap(_ => modelPub.remove(id))

      override def listForModel(modelId: Long): F[List[ModelVersion]] =
        for {
          rows <- listVersionsQ(modelId).to[List].transact(tx)
        } yield rows.flatMap(toModelVersionT(_).toOption)

      override def lastModelVersionByModel(modelId: Long): F[Option[ModelVersion]] =
        for {
          row <- lastModelVersionQ(modelId).option.transact(tx)
        } yield row.flatMap(toModelVersionT(_).toOption)
    }
}
