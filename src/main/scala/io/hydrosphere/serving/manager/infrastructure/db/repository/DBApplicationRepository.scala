package io.hydrosphere.serving.manager.infrastructure.db.repository

import cats.data.{NonEmptyList, OptionT}
import cats.effect.Bracket
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import io.circe.parser._
import io.circe.syntax._
import io.hydrosphere.serving.manager.domain.application._
import io.hydrosphere.serving.manager.domain.application.graph._
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.hydrosphere.serving.manager.util.CollectionOps._
import io.hydrosphere.serving.manager.infrastructure.db.Metas._
import io.hydrosphere.serving.manager.util.JsonOps._
import DBApplicationRepository._

class DBApplicationRepository[F[_]](tx: Transactor[F])(implicit F: Bracket[F, Throwable])
    extends ApplicationRepository[F] {
  override def create(entity: Application): F[Application] = {
    val row = fromApplication(entity)
    for {
      id <- createQ(row).withUniqueGeneratedKeys[Long]("id").transact(tx)
    } yield entity.copy(id = id)
  }

  override def get(id: Long): F[Option[Application]] = {
    val result = for {
      app                   <- OptionT(getByIdQ(id).option.transact(tx))
      (versions, servables) <- OptionT.liftF(fetchAppInfo(tx, app))
      res                   <- OptionT.liftF(F.fromEither(toApplication(app, versions, servables)))
    } yield res
    result.value
  }

  override def get(name: String): F[Option[Application]] = {
    val result = for {
      app                   <- OptionT(getByNameQ(name).option.transact(tx))
      (versions, servables) <- OptionT.liftF(fetchAppInfo(tx, app))
      app                   <- OptionT.liftF(F.fromEither(toApplication(app, versions, servables)))
    } yield app
    result.value
  }

  override def update(value: Application): F[Int] = {
    val row = fromApplication(value)
    updateQ(row).run.transact(tx)
  }

  override def delete(id: Long): F[Int] =
    deleteQ(id).run.transact(tx)

  override def all(): F[List[Application]] =
    for {
      apps                      <- allQ.to[List].transact(tx)
      (versionMap, servableMap) <- fetchAppsInfo(tx, apps)
      res                       <- F.fromEither(apps.traverse(app => toApplication(app, versionMap, servableMap)))
    } yield res

  override def findVersionUsage(versionId: Long): F[List[Application]] =
    for {
      apps                      <- modelVersionUsageQ(versionId).to[List].transact(tx)
      (versionMap, servableMap) <- fetchAppsInfo(tx, apps)
      res                       <- F.fromEither(apps.traverse(app => toApplication(app, versionMap, servableMap)))
    } yield res

  override def findServableUsage(servableName: String): F[List[Application]] =
    for {
      apps                      <- servableUsageQ(servableName).to[List].transact(tx)
      (versionMap, servableMap) <- fetchAppsInfo(tx, apps)
      res                       <- F.fromEither(apps.traverse(app => toApplication(app, versionMap, servableMap)))
    } yield res

}

object DBApplicationRepository {
  final case class ApplicationRow(
      id: Long,
      application_name: String,
      namespace: Option[String],
      status: String,
      application_contract: Signature,
      execution_graph: String,
      used_servables: List[String],
      kafka_streams: List[String],
      status_message: Option[String],
      used_model_versions: List[Long],
      metadata: Option[String]
  )

  def toApplication(
      ar: ApplicationRow,
      versions: Map[Long, ModelVersion.Internal],
      servables: Map[String, Servable]
  ): Either[Throwable, Application] =
    for {
      graph <- ar.execution_graph.parseJsonAs[ApplicationGraph]
      kafkaStreaming <-
        ar.kafka_streams.traverse(p => parse(p).flatMap(_.as[ApplicationKafkaStream]))
      metadata =
        ar.metadata
          .flatMap(str => parse(str).flatMap(_.as[Map[String, String]]).toOption)
          .getOrElse(Map.empty)
      status =
        Application.Status.withNameInsensitiveOption(ar.status).getOrElse(Application.Status.Failed)
      message = ar.status_message.getOrElse("")
    } yield Application(
      id = ar.id,
      name = ar.application_name,
      signature = ar.application_contract,
      kafkaStreaming = kafkaStreaming,
      namespace = ar.namespace,
      status = status,
      executionGraph = graph,
      metadata = metadata,
      message = message
    )

  def fromApplication(app: Application): ApplicationRow = {
    val servables = app.executionGraph.nodes.toList.flatMap { x =>
      x.variants.toList.flatMap(_.servable.map(_.fullName))
    }
    val models = app.executionGraph.nodes.toList.flatMap { x =>
      x.variants.toList.map(_.modelVersion.id)
    }
    ApplicationRow(
      id = app.id,
      application_name = app.name,
      namespace = app.namespace,
      status = app.status.entryName,
      application_contract = app.signature,
      execution_graph = app.executionGraph.asJson.noSpaces,
      used_servables = servables,
      used_model_versions = models,
      kafka_streams = app.kafkaStreaming.map(_.asJson.noSpaces),
      status_message = app.message.some,
      metadata = app.metadata.maybeEmpty.map(_.asJson.noSpaces)
    )
  }

  case class AppDBSchemaErrors(errs: List[AppDBSchemaError]) extends Throwable

  sealed trait AppDBSchemaError extends Throwable with Serializable with Product

  case class IncompatibleExecutionGraphError(app: ApplicationRow) extends AppDBSchemaError

  case class UsingModelVersionIsMissing(
      app: ApplicationRow,
      graph: Either[VersionGraphAdapter, ServableGraphAdapter]
  ) extends AppDBSchemaError

  case class UsingServableIsMissing(app: ApplicationRow, servableName: String)
      extends AppDBSchemaError

  case class InvalidAppStatus(app: ApplicationRow) extends AppDBSchemaError

  def allQ =
    sql"""SELECT * FROM hydro_serving.application""".query[ApplicationRow]

  def getByNameQ(name: String) =
    sql"""
         |SELECT * FROM hydro_serving.application
         | WHERE application_name = $name""".stripMargin.query[ApplicationRow]

  def getByIdQ(id: Long) =
    sql"""
         |SELECT * FROM hydro_serving.application
         | WHERE id = $id""".stripMargin.query[ApplicationRow]

  def modelVersionUsageQ(versionId: Long) =
    sql"""
         |SELECT * FROM hydro_serving.application
         | WHERE ${versionId} = ANY(used_model_versions)""".stripMargin.query[ApplicationRow]

  def servableUsageQ(servableName: String) =
    sql"""
         |SELECT * FROM hydro_serving.application
         | WHERE ${servableName} = ANY(used_servables)""".stripMargin.query[ApplicationRow]

  def createQ(app: ApplicationRow) =
    sql"""
         |INSERT INTO hydro_serving.application(
         | application_name,
         | used_servables,
         | used_model_versions,
         | application_contract,
         | execution_graph,
         | kafka_streams,
         | status,
         | status_message
         |) VALUES (
         | ${app.application_name},
         | ${app.used_servables},
         | ${app.used_model_versions},
         | ${app.application_contract},
         | ${app.execution_graph},
         | ${app.kafka_streams},
         | ${app.status},
         | ${app.status_message}
         |)""".stripMargin.update

  def updateQ(app: ApplicationRow) =
    sql"""
         |UPDATE hydro_serving.application SET
         | application_name = ${app.application_name},
         | used_servables = ${app.used_servables},
         | used_model_versions = ${app.used_model_versions},
         | application_contract = ${app.application_contract},
         | execution_graph = ${app.execution_graph},
         | kafka_streams = ${app.kafka_streams},
         | status = ${app.status},
         | status_message = ${app.status_message}
         | WHERE id = ${app.id}""".stripMargin.update

  def deleteQ(id: Long) =
    sql"""
         |DELETE FROM hydro_serving.application
         | WHERE id = $id""".stripMargin.update

  def fetchAppInfo[F[_]](
      tx: Transactor[F],
      app: ApplicationRow
  )(implicit
      F: Bracket[F, Throwable]
  ): F[(Map[Long, ModelVersion.Internal], Map[String, Servable])] =
    fetchAppsInfo(tx, app :: Nil)

  def fetchAppsInfo[F[_]](
      tx: Transactor[F],
      apps: List[ApplicationRow]
  )(implicit
      F: Bracket[F, Throwable]
  ): F[(Map[Long, ModelVersion.Internal], Map[String, Servable])] =
    for {
      versions <-
        NonEmptyList
          .fromList(apps.flatMap(_.used_model_versions)) match {
          case Some(x) =>
            DBModelVersionRepository
              .findVersionsQ(x)
              .to[List]
              .transact(tx)
              .map { list =>
                list
                  .map(DBModelVersionRepository.toModelVersionT)
                  .collect { case x: ModelVersion.Internal => x }
              }
          case None => List.empty[ModelVersion.Internal].pure[F]
        }

      servables <-
        NonEmptyList
          .fromList(apps.flatMap(_.used_servables)) match {
          case Some(x) =>
            DBServableRepository
              .getManyQ(x)
              .to[List]
              .transact(tx)
              .map(list =>
                list
                  .map(x => DBServableRepository.toServableT(x))
                  .collect { case Right(x) => x }
              )
          case None => List.empty[Servable].pure[F]
        }

      versionMap  = versions.map(x => x.id -> x).toMap
      servableMap = servables.map(x => x.fullName -> x).toMap
    } yield versionMap -> servableMap

}
