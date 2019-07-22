package io.hydrosphere.serving.manager.infrastructure.db.repository

import java.time.LocalDateTime

import cats.implicits._
import cats.effect.Sync
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.data_profile_types.DataProfileType
import io.hydrosphere.serving.manager.domain.host_selector.HostSelector
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionRepository, ModelVersionStatus}
import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol._
import spray.json._
import doobie.implicits._
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBHostSelectorRepository.HostSelectorRow
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBModelRepository.ModelRow

//class DBModelVersionRepository[F[_]: Sync](
//  implicit tx: Transactor[F],
//) extends ModelVersionRepository[F] {
//
//  import DBModelVersionRepository._
//  import databaseService._
//  import databaseService.driver.api._
//
//  def joinedQ = Tables.ModelVersion
//    .join(Tables.Model)
//    .on((mv, m) => mv.modelId === m.modelId)
//    .joinLeft(Tables.HostSelector)
//    .on((m, hs) => hs.hostSelectorId === m._1.hostSelector)
//    .map {
//      case ((mv, m), hs) => (mv, m, hs)
//    }
//
//  override def create(entity: ModelVersion): F[ModelVersion] = AsyncUtil.futureAsync {
//    db.run(
//      Tables.ModelVersion returning Tables.ModelVersion += Tables.ModelVersionRow(
//        modelVersionId = entity.id,
//        modelId = entity.model.id,
//        modelVersion = entity.modelVersion,
//        modelContract = entity.modelContract.toJson.compactPrint,
//        createdTimestamp = entity.created,
//        imageName = entity.image.name,
//        imageTag = entity.image.tag,
//        imageSha256 = entity.image.sha256,
//        hostSelector = entity.hostSelector.map(_.id),
//        finishedTimestamp = entity.finished,
//        runtimeName = entity.runtime.name,
//        runtimeVersion = entity.runtime.tag,
//        status = entity.status.toString,
//        profileTypes = if (entity.profileTypes.isEmpty) None else Some(entity.profileTypes.toJson.compactPrint),
//        installCommand = entity.installCommand,
//        metadata = if (entity.metadata.isEmpty) None else Some(entity.metadata.toJson.compactPrint)
//      )
//    ).map(x => mapFromDb(x, entity.model, entity.hostSelector))
//  }
//
//  override def get(id: Long): F[Option[ModelVersion]] = AsyncUtil.futureAsync {
//    db.run(
//      joinedQ
//        .filter(mv => mv._1.modelVersionId === id)
//        .result.headOption
//    ).map(x => x.map(mapFromDb))
//  }
//
//  override def delete(id: Long): F[Int] = AsyncUtil.futureAsync {
//    db.run(
//      Tables.ModelVersion
//        .filter(_.modelVersionId === id)
//        .delete
//    )
//  }
//
//  override def all(): F[Seq[ModelVersion]] = AsyncUtil.futureAsync {
//    db.run(joinedQ.result).map(_.map(mapFromDb))
//  }
//
//  override def lastModelVersionByModel(modelId: Long, max: Int): F[Seq[ModelVersion]] = AsyncUtil.futureAsync {
//    db.run(
//      joinedQ
//        .filter(_._1.modelId === modelId)
//        .sortBy(_._1.modelVersion.desc)
//        .take(max)
//        .result
//    ).map(_.map(mapFromDb))
//  }
//
//  override def listForModel(modelId: Long): F[Seq[ModelVersion]] = AsyncUtil.futureAsync {
//    db.run {
//      joinedQ
//        .filter(_._1.modelId === modelId)
//        .sortBy(_._1.modelVersion)
//        .result
//    }.map(_.map(mapFromDb))
//  }
//
//  override def get(modelName: String, modelVersion: Long): F[Option[ModelVersion]] = AsyncUtil.futureAsync {
//    db.run {
//      joinedQ
//        .filter(mv => (mv._1.modelVersion === modelVersion) && (mv._2.name === modelName))
//        .result.headOption
//    }.map(_.map(mapFromDb))
//  }
//
//  override def update(id: Long, entity: ModelVersion): F[Int] = AsyncUtil.futureAsync {
//    val a = for {
//      mv <- Tables.ModelVersion if mv.modelVersionId === id
//    } yield (mv.finishedTimestamp, mv.status, mv.imageSha256)
//    db.run(a.update((entity.finished, entity.status.toString, entity.image.sha256)))
//  }
//
//  override def get(idx: Seq[Long]): F[Seq[ModelVersion]] = AsyncUtil.futureAsync {
//    db.run(
//      joinedQ
//        .filter(_._1.modelVersionId inSetBind idx)
//        .result
//    ).map(_.map(mapFromDb))
//  }
//}

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
    modelContract = ModelContract.fromAscii(mvr.model_contract),
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

  def allQ = sql"""
                  |SELECT * FROM hydro_serving.model_version
                  |	 LEFT JOIN hydro_serving.model ON hydro_serving.model_version.model_id = hydro_serving.model.model_id
                  |	 LEFT JOIN hydro_serving.host_selector ON hydro_serving.model_version.host_selector = hydro_serving.host_selector.host_selector_id
    """.stripMargin.query[(ModelVersionRow, ModelRow, Option[HostSelectorRow])]

  def make[F[_] : Sync](tx: Transactor[F]) = new ModelVersionRepository[F] {
    override def all(): F[Seq[ModelVersion]] = {
      for {
        rows <- allQ.to[Seq].transact(tx)
      } yield rows.map((toModelVersion _).tupled)
    }

    override def create(entity: ModelVersion): F[ModelVersion] = ???

    override def update(id: Long, entity: ModelVersion): F[Int] = ???

    override def get(id: Long): F[Option[ModelVersion]] = ???

    override def get(modelName: String, modelVersion: Long): F[Option[ModelVersion]] = ???

    override def get(idx: Seq[Long]): F[Seq[ModelVersion]] = ???

    override def delete(id: Long): F[Int] = ???

    override def listForModel(modelId: Long): F[Seq[ModelVersion]] = ???

    override def lastModelVersionByModel(modelId: Long, max: Int): F[Seq[ModelVersion]] = ???
  }

  //  def mapFromDb(x: (Tables.ModelVersionRow, Tables.ModelRow, Option[Tables.HostSelectorRow])): ModelVersion = {
  //    val m = DBModelRepository.mapFromDb(x._2)
  //    val hs = DBHostSelectorRepository.mapFromDb(x._3)
  //    mapFromDb(x._1, m, hs)
  //  }
  //
  //  def mapFromDb(x: Tables.ModelVersionRow, model: Model, hostSelector: Option[HostSelector]): ModelVersion = {
  //    ModelVersion(
  //      id = x.modelVersionId,
  //      image = DockerImage(
  //        name = x.imageName,
  //        tag = x.imageTag,
  //        sha256 = x.imageSha256,
  //      ),
  //      created = x.createdTimestamp,
  //      finished = x.finishedTimestamp,
  //      modelVersion = x.modelVersion,
  //      modelContract = x.modelContract.parseJson.convertTo[ModelContract],
  //      runtime = DockerImage(
  //        name = x.runtimeName,
  //        tag = x.runtimeVersion
  //      ),
  //      model = model,
  //      hostSelector = hostSelector,
  //      status = ModelVersionStatus.withName(x.status),
  //      profileTypes = x.profileTypes.map(_.parseJson.convertTo[Map[String, DataProfileType]]).getOrElse(Map.empty),
  //      installCommand = x.installCommand,
  //      metadata = x.metadata.map(_.parseJson.convertTo[Map[String, String]]).getOrElse(Map.empty)
  //    )
  //  }
}