package io.hydrosphere.serving.manager.infrastructure.db.repository

import cats.effect.Bracket
import cats.implicits._
import doobie._
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
import java.time.Instant

import cats.data.NonEmptyList
import io.hydrosphere.serving.manager.domain.monitoring.MonitoringConfiguration
import io.hydrosphere.serving.manager.util.SprayDoobie._

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
    metadata: Option[String],
    is_external: Boolean,
    monitoring_configuration: Option[MonitoringConfiguration]
  )

  type JoinedModelVersionRow = (ModelVersionRow, ModelRow, Option[HostSelectorRow])

  def toModelVersion(mvr: ModelVersionRow, mr: ModelRow, hsr: Option[HostSelectorRow]): ModelVersion = {
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
    val contract = mvr.model_contract.parseJson.convertTo[ModelContract]
    val metadata = mvr.metadata.map(_.parseJson.convertTo[Map[String, String]]).getOrElse(Map.empty)
    if (mvr.is_external) {
      ModelVersion.External(
        id = mvr.model_version_id,
        created = mvr.created_timestamp,
        modelVersion = mvr.model_version,
        modelContract = contract,
        model = model,
        metadata = metadata,
        monitoringConfiguration = mvr.monitoring_configuration
      )
    } else {
      ModelVersion.Internal(
        id = mvr.model_version_id,
        image = image,
        created = mvr.created_timestamp,
        finished = mvr.finished_timestamp,
        modelVersion = mvr.model_version,
        modelContract = contract,
        runtime = runtime,
        model = model,
        hostSelector = hsr.map { h =>
          HostSelector(
            id = h.host_selector_id,
            name = h.name,
            nodeSelector = h.node_selector
          )
        },
        status = ModelVersionStatus.withName(mvr.status),
        installCommand = mvr.install_command,
        metadata = metadata,
        monitoringConfiguration = mvr.monitoring_configuration,
      )
    }
  }

  def fromModelVersion(amv: ModelVersion): ModelVersionRow = {
    amv match {
      case mv: ModelVersion.Internal =>
        ModelVersionRow(
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
          metadata = if(mv.metadata.nonEmpty) {mv.metadata.toJson.compactPrint.some} else None,
          is_external = false,
          monitoring_configuration = mv.monitoringConfiguration
        )
      case ModelVersion.External(id, created, modelVersion, modelContract, model, metadata, monitoring_configuration) =>
        ModelVersionRow(
          model_version_id = id,
          model_id = model.id,
          host_selector = None,
          created_timestamp = created,
          finished_timestamp = Some(created),
          model_version = modelVersion,
          model_contract = modelContract.toJson.compactPrint,
          image_name = DockerImage.dummyImage.name,
          image_tag = DockerImage.dummyImage.tag,
          image_sha256 = None,
          runtime_name = DockerImage.dummyImage.name,
          runtime_version = DockerImage.dummyImage.tag,
          status = ModelVersionStatus.Released.toString,
          profile_types = None,
          install_command = None,
          metadata = if(metadata.nonEmpty) {metadata.toJson.compactPrint.some} else None,
          is_external = true,
          monitoring_configuration = monitoring_configuration
        )
    }
  }

  def toModelVersionT = (toModelVersion _).tupled

  implicit val han = LogHandler.jdkLogHandler


  def allQ: doobie.Query0[JoinedModelVersionRow] = {
    sql"""
         |SELECT * FROM hydro_serving.model_version
         |  LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         |  LEFT JOIN hydro_serving.host_selector ON hydro_serving.model_version.host_selector = hydro_serving.host_selector.host_selector_id
         |""".stripMargin.queryWithLogHandler[JoinedModelVersionRow](LogHandler.jdkLogHandler)
  }

  def getQ(id: Long): doobie.Query0[JoinedModelVersionRow] = {
    sql"""
         |SELECT * FROM hydro_serving.model_version
         |  LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         |	LEFT JOIN hydro_serving.host_selector ON hydro_serving.model_version.host_selector = hydro_serving.host_selector.host_selector_id
         |  WHERE hydro_serving.model_version.model_version_id = $id
         |""".stripMargin.queryWithLogHandler[JoinedModelVersionRow](LogHandler.jdkLogHandler)
  }

  def getQ(name: String, version: Long): doobie.Query0[JoinedModelVersionRow] = {
    sql"""
         |SELECT * FROM hydro_serving.model_version
         |  LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         |	LEFT JOIN hydro_serving.host_selector ON hydro_serving.model_version.host_selector = hydro_serving.host_selector.host_selector_id
         |  WHERE hydro_serving.model.name = $name AND model_version = $version
         |""".stripMargin.queryWithLogHandler[JoinedModelVersionRow](LogHandler.jdkLogHandler)
  }

  def listVersionsQ(modelId: Long): doobie.Query0[JoinedModelVersionRow] = {
    sql"""
         |SELECT * FROM hydro_serving.model_version
         |  LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         |	LEFT JOIN hydro_serving.host_selector ON hydro_serving.model_version.host_selector = hydro_serving.host_selector.host_selector_id
         |  WHERE hydro_serving.model_version.model_id = $modelId
         |""".stripMargin.queryWithLogHandler[JoinedModelVersionRow](LogHandler.jdkLogHandler)
  }

  def findVersionsQ(versionIdx: NonEmptyList[Long]): doobie.Query0[JoinedModelVersionRow] = {
    val q =
      fr"""
          |SELECT * FROM hydro_serving.model_version
          | LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
          |	LEFT JOIN hydro_serving.host_selector ON hydro_serving.model_version.host_selector = hydro_serving.host_selector.host_selector_id
          |  WHERE """.stripMargin
    val fullQ = q ++ Fragments.in(fr"hydro_serving.model_version.model_version_id", versionIdx)
    fullQ.queryWithLogHandler[JoinedModelVersionRow](LogHandler.jdkLogHandler)
  }

  def lastModelVersionQ(modelId: Long): doobie.Query0[JoinedModelVersionRow] = {
    sql"""
         |SELECT * FROM hydro_serving.model_version
         |  LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
         |	LEFT JOIN hydro_serving.host_selector ON hydro_serving.model_version.host_selector = hydro_serving.host_selector.host_selector_id
         |  WHERE hydro_serving.model_version.model_id = $modelId
         |  ORDER BY hydro_serving.model_version.model_version DESC
         |  LIMIT 1
         |""".stripMargin.queryWithLogHandler[JoinedModelVersionRow](LogHandler.jdkLogHandler)
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
         | metadata,
         | is_external,
         | monitoring_configuration
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
         | ${mv.metadata},
         | ${mv.is_external},
         | ${mv.monitoring_configuration}
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
         | metadata = ${mv.metadata},
         | monitoring_configuration = ${mv.monitoring_configuration}
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
      } yield entity match {
        case imv:  ModelVersion.Internal => imv.copy(id = id)
        case emv:  ModelVersion.External => emv.copy(id = id)
      }
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