package io.hydrosphere.serving.manager.infrastructure.db.repository

import java.time.LocalDateTime

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.domain.host_selector.HostSelector
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionRepository, ModelVersionStatus}
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBHostSelectorRepository.HostSelectorRow
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBModelRepository.ModelRow
import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol._
import spray.json._

// TODO finished_timestamp is not set when build fails
object DBModelVersionRepository {

  final case class ModelVersionRow(
    model_version_id: Long,
    model_id: Long,
    host_selector: Option[Long],
    created_timestamp: LocalDateTime,
    finished_timestamp: Option[LocalDateTime],
    model_version: Long,
    model_contract: String,
    image_name: String,
    image_tag: String,
    image_sha256: Option[String],
    runtime_name: String,
    runtime_version: String,
    status: String,
    profile_types: Option[String],
    install_command: Option[String],
    metadata: Option[String]
  )

  def toModelVersion(mvr: ModelVersionRow, mr: ModelRow, hsr: Option[HostSelectorRow]): ModelVersion = ModelVersion(
    id = mvr.model_version_id,
    image = DockerImage(
      name = mvr.image_name,
      tag = mvr.image_tag,
      sha256 = mvr.image_sha256
    ),
    created = mvr.created_timestamp,
    finished = mvr.finished_timestamp,
    modelVersion = mvr.model_version,
    modelContract = mvr.model_contract.parseJson.convertTo[ModelContract],
    runtime = DockerImage(
      name = mvr.runtime_name,
      tag = mvr.runtime_version
    ),
    model = Model(
      id = mr.model_id,
      name = mr.name
    ),
    hostSelector = hsr.map { h =>
      HostSelector(
        id = h.host_selector_id,
        name = h.name,
        nodeSelector = h.node_selector
      )
    },
    status = ModelVersionStatus.withName(mvr.status),
    installCommand = mvr.install_command,
    metadata = mvr.metadata.map(_.parseJson.convertTo[Map[String, String]]).getOrElse(Map.empty)
  )

  def allQ =
    sql"""
         |SELECT * FROM hydro_serving.model_version
         |  LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         |  LEFT JOIN hydro_serving.host_selector ON hydro_serving.model_version.host_selector = hydro_serving.host_selector.host_selector_id
         |""".stripMargin.query[(ModelVersionRow, ModelRow, Option[HostSelectorRow])]

  def getQ(id: Long) =
    sql"""
         |SELECT * FROM hydro_serving.model_version
         |  LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         |	LEFT JOIN hydro_serving.host_selector ON hydro_serving.model_version.host_selector = hydro_serving.host_selector.host_selector_id
         |  WHERE model_version_id = $id
         |""".stripMargin.query[(ModelVersionRow, ModelRow, Option[HostSelectorRow])]

  def getQ(name: String, version: Long) =
    sql"""
         |SELECT * FROM hydro_serving.model_version
         |  LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         |	LEFT JOIN hydro_serving.host_selector ON hydro_serving.model_version.host_selector = hydro_serving.host_selector.host_selector_id
         |  WHERE name = $name AND model_version = $version
         |""".stripMargin.query[(ModelVersionRow, ModelRow, Option[HostSelectorRow])]

  def listVersionsQ(modelId: Long) =
    sql"""
         |SELECT * FROM hydro_serving.model_version
         |  LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         |	LEFT JOIN hydro_serving.host_selector ON hydro_serving.model_version.host_selector = hydro_serving.host_selector.host_selector_id
         |  WHERE model_id = $modelId
         |""".stripMargin.query[(ModelVersionRow, ModelRow, Option[HostSelectorRow])]

    def lastModelVersionQ(modelId: Long) =
    sql"""
         |SELECT * FROM hydro_serving.model_version
         |  LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         |	LEFT JOIN hydro_serving.host_selector ON hydro_serving.model_version.host_selector = hydro_serving.host_selector.host_selector_id
         |  WHERE model_id = $modelId
         |  ORDER BY model_version DESC
         |  LIMIT 1
         |""".stripMargin.query[(ModelVersionRow, ModelRow, Option[HostSelectorRow])]

  def insertQ(mv: ModelVersion) =
    sql"""
         |INSERT INTO hydro_serving.model_version (
         | model_id,
         | host_selector,
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
         | metadata
         | ) VALUES (
         | ${mv.model.id},
         | ${mv.hostSelector.map(_.id)},
         | ${mv.created},
         | ${mv.finished},
         | ${mv.modelVersion},
         | ${mv.modelContract.toJson.compactPrint},
         | ${mv.image.name},
         | ${mv.image.tag},
         | ${mv.image.sha256},
         | ${mv.runtime.name},
         | ${mv.runtime.tag},
         | ${mv.status.toString},
         | ${mv.installCommand},
         | ${mv.metadata.toJson.compactPrint}
         |)""".stripMargin.update

  def updateQ(mv: ModelVersion) =
    sql"""
         |UPDATE hydro_serving.model_version SET
         | model_id = ${mv.model.id},
         | host_selector = ${mv.hostSelector.map(_.id)},
         | created_timestamp = ${mv.created},
         | finished_timestamp = ${mv.finished},
         | model_version = ${mv.modelVersion},
         | model_contract = ${mv.modelContract.toJson.compactPrint},
         | image_name = ${mv.image.name},
         | image_tag = ${mv.image.tag},
         | image_sha256 = ${mv.image.sha256},
         | runtime_name = ${mv.runtime.name},
         | runtime_version = ${mv.runtime.tag},
         | status = ${mv.status.toString},
         | install_command = ${mv.installCommand},
         | metadata = ${mv.metadata.toJson.compactPrint}
         | WHERE model_version_id = ${mv.id}""".stripMargin.update

  def deleteQ(id: Long) =
    sql"""
         |DELETE FROM hydro_serving.model_version
         |  WHERE model_version_id = $id
      """.stripMargin.update


  def make[F[_] : Sync](tx: Transactor[F]) = new ModelVersionRepository[F] {
    override def all(): F[Seq[ModelVersion]] = {
      for {
        rows <- allQ.to[Seq].transact(tx)
      } yield rows.map((toModelVersion _).tupled)
    }

    override def create(entity: ModelVersion): F[ModelVersion] = {
      for {
        id <- insertQ(entity).withUniqueGeneratedKeys[Long]("model_version_id").transact(tx)
      } yield entity.copy(id = id)
    }

    override def update(entity: ModelVersion): F[Int] = {
      updateQ(entity).run.transact(tx)
    }

    override def get(id: Long): F[Option[ModelVersion]] = {
      for {
        row <- getQ(id).option.transact(tx)
      } yield row.map((toModelVersion _).tupled)
    }

    override def get(modelName: String, modelVersion: Long): F[Option[ModelVersion]] = {
      for {
        row <- getQ(modelName, modelVersion).option.transact(tx)
      } yield row.map((toModelVersion _).tupled)
    }

    override def delete(id: Long): F[Int] = {
      deleteQ(id).run.transact(tx)
    }

    override def listForModel(modelId: Long): F[Seq[ModelVersion]] = {
      for {
        rows <- listVersionsQ(modelId).to[Seq].transact(tx)
      } yield rows.map((toModelVersion _).tupled)
    }

    override def lastModelVersionByModel(modelId: Long): F[Seq[ModelVersion]] = {
      for {
        row <- lastModelVersionQ(modelId).option.transact(tx)
      } yield row.map((toModelVersion _).tupled)
    }
  }
}