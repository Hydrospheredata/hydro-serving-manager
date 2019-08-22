package io.hydrosphere.serving.manager.infrastructure.db.repository

import java.time.LocalDateTime

import cats.effect.Bracket
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
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
import java.time.Instant

import cats.data.NonEmptyList

object DBModelVersionRepository {
  final case class ModelVersionRow(
    model_version_id: Long,
    model_id: Long,
    host_selector: Option[Long],
    created_timestamp: Instant,
    finished_timestamp: Option[Instant],
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

  type JoinedModelVersionRow = (ModelVersionRow, ModelRow, Option[HostSelectorRow])

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

  def fromModelVersion(mv: ModelVersion) = ModelVersionRow(
    model_version_id = mv.id,
    model_id = mv.model.id,
    host_selector = mv.hostSelector.map(_.id),
    created_timestamp = mv.created,
    finished_timestamp = mv.finished,
    model_version = mv.modelVersion,
    model_contract = mv.modelContract.toJson.compactPrint,
    image_name = mv.image.name,
    image_tag = mv.image.tag,
    image_sha256 = mv.image.sha256,
    runtime_name = mv.runtime.name,
    runtime_version = mv.runtime.tag,
    status = mv.status.toString,
    profile_types = None,
    install_command = mv.installCommand,
    metadata = if(mv.metadata.nonEmpty) {mv.metadata.toJson.compactPrint.some} else {None}
  )

  def toModelVersionT = (toModelVersion _).tupled

  def allQ: doobie.Query0[JoinedModelVersionRow] = {
    sql"""
         |SELECT * FROM hydro_serving.model_version
         |  LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         |  LEFT JOIN hydro_serving.host_selector ON hydro_serving.model_version.host_selector = hydro_serving.host_selector.host_selector_id
         |""".stripMargin.query[JoinedModelVersionRow]
  }

  def getQ(id: Long): doobie.Query0[JoinedModelVersionRow] = {
    sql"""
         |SELECT * FROM hydro_serving.model_version
         |  LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         |	LEFT JOIN hydro_serving.host_selector ON hydro_serving.model_version.host_selector = hydro_serving.host_selector.host_selector_id
         |  WHERE hydro_serving.model_version.model_version_id = $id
         |""".stripMargin.query[JoinedModelVersionRow]
  }

  def getQ(name: String, version: Long): doobie.Query0[JoinedModelVersionRow] = {
    sql"""
         |SELECT * FROM hydro_serving.model_version
         |  LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         |	LEFT JOIN hydro_serving.host_selector ON hydro_serving.model_version.host_selector = hydro_serving.host_selector.host_selector_id
         |  WHERE hydro_serving.model.name = $name AND model_version = $version
         |""".stripMargin.query[JoinedModelVersionRow]
  }

  def listVersionsQ(modelId: Long): doobie.Query0[JoinedModelVersionRow] = {
    sql"""
         |SELECT * FROM hydro_serving.model_version
         |  LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         |	LEFT JOIN hydro_serving.host_selector ON hydro_serving.model_version.host_selector = hydro_serving.host_selector.host_selector_id
         |  WHERE hydro_serving.model_version.model_id = $modelId
         |""".stripMargin.query[JoinedModelVersionRow]
  }

  def findVersionsQ(versionIdx: NonEmptyList[Long]): doobie.Query0[JoinedModelVersionRow] = {
    val q =
      fr"""
          |SELECT * FROM hydro_serving.model_version
          | LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
          |	LEFT JOIN hydro_serving.host_selector ON hydro_serving.model_version.host_selector = hydro_serving.host_selector.host_selector_id
          |  WHERE """.stripMargin
    val fullQ = q ++ Fragments.in(fr"hydro_serving.model_version.model_version_id", versionIdx)
    fullQ.query[JoinedModelVersionRow]
  }

  def lastModelVersionQ(modelId: Long): doobie.Query0[JoinedModelVersionRow] = {
    sql"""
         |SELECT * FROM hydro_serving.model_version
         |  LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         |	LEFT JOIN hydro_serving.host_selector ON hydro_serving.model_version.host_selector = hydro_serving.host_selector.host_selector_id
         |  WHERE hydro_serving.model_version.model_id = $modelId
         |  ORDER BY hydro_serving.model_version.model_version DESC
         |  LIMIT 1
         |""".stripMargin.query[JoinedModelVersionRow]
  }

  def insertQ(mv: ModelVersionRow): doobie.Update0 = {
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
         | ${mv.model_id},
         | ${mv.host_selector},
         | ${mv.created_timestamp},
         | ${mv.finished_timestamp},
         | ${mv.model_version},
         | ${mv.model_contract},
         | ${mv.image_name},
         | ${mv.image_tag},
         | ${mv.image_sha256},
         | ${mv.runtime_name},
         | ${mv.runtime_version},
         | ${mv.status},
         | ${mv.install_command},
         | ${mv.metadata}
         |)""".stripMargin.update
  }

  def updateQ(mv: ModelVersionRow): doobie.Update0 = {
    sql"""
         |UPDATE hydro_serving.model_version SET
         | model_id = ${mv.model_id},
         | host_selector = ${mv.host_selector},
         | created_timestamp = ${mv.created_timestamp},
         | finished_timestamp = ${mv.finished_timestamp},
         | model_version = ${mv.model_version},
         | model_contract = ${mv.model_contract},
         | image_name = ${mv.image_name},
         | image_tag = ${mv.image_tag},
         | image_sha256 = ${mv.image_sha256},
         | runtime_name = ${mv.runtime_name},
         | runtime_version = ${mv.runtime_version},
         | status = ${mv.status},
         | install_command = ${mv.install_command},
         | metadata = ${mv.metadata}
         | WHERE model_version_id = ${mv.model_version_id}""".stripMargin.update
  }

  def deleteQ(id: Long): doobie.Update0 = {
    sql"""
         |DELETE FROM hydro_serving.model_version
         |  WHERE model_version_id = $id
      """.stripMargin.update
  }

  def make[F[_]]()(implicit F: Bracket[F, Throwable], tx: Transactor[F]): ModelVersionRepository[F] = new ModelVersionRepository[F] {
    override def all(): F[List[ModelVersion]] = {
      for {
        rows <- allQ.to[List].transact(tx)
      } yield rows.map((toModelVersion _).tupled)
    }

    override def create(entity: ModelVersion): F[ModelVersion] = {
      for {
        id <- insertQ(fromModelVersion(entity)).withUniqueGeneratedKeys[Long]("model_version_id").transact(tx)
      } yield entity.copy(id = id)
    }

    override def update(entity: ModelVersion): F[Int] = {
      updateQ(fromModelVersion( entity)).run.transact(tx)
    }

    override def get(id: Long): F[Option[ModelVersion]] = {
      for {
        row <- getQ(id).option.transact(tx)
      } yield row.map(toModelVersionT)
    }

    override def get(modelName: String, modelVersion: Long): F[Option[ModelVersion]] = {
      for {
        row <- getQ(modelName, modelVersion).option.transact(tx)
      } yield row.map(toModelVersionT)
    }

    override def delete(id: Long): F[Int] = {
      deleteQ(id).run.transact(tx)
    }

    override def listForModel(modelId: Long): F[List[ModelVersion]] = {
      for {
        rows <- listVersionsQ(modelId).to[List].transact(tx)
      } yield rows.map(toModelVersionT)
    }

    override def lastModelVersionByModel(modelId: Long): F[Option[ModelVersion]] = {
      for {
        row <- lastModelVersionQ(modelId).option.transact(tx)
      } yield row.map(toModelVersionT)
    }
  }
}