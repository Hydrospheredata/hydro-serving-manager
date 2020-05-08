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
import io.hydrosphere.serving.manager.domain.application.Application.GenericApplication
import io.hydrosphere.serving.manager.domain.application._
import io.hydrosphere.serving.manager.domain.application.graph.VersionGraphComposer.PipelineStage
import io.hydrosphere.serving.manager.domain.application.graph._
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.hydrosphere.serving.manager.util.CollectionOps._
import io.hydrosphere.serving.manager.infrastructure.db.Metas._

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
  ): Either[Throwable, GenericApplication] =
    for {
      jsonGraph <- parse(ar.execution_graph)
      (status, graph) <- ar.status match {
        case "Assembling" =>
          for {
            adapterGraph <- jsonGraph.as[VersionGraphAdapter]
            mappedStages <- adapterGraph.stages.traverse { stage =>
              val signature = stage.signature
              val variants = stage.modelVariants.traverse { m =>
                versions
                  .get(m.modelVersion.id)
                  .toRight(UsingModelVersionIsMissing(ar, adapterGraph.asLeft))
                  .map(Variant(_, m.weight))
              }
              variants.map(PipelineStage(_, signature))
            }
          } yield Application.Assembling -> mappedStages

        case "Failed" =>
          for {
            adapterGraph <- jsonGraph.as[VersionGraphAdapter]
            mappedStages <- adapterGraph.stages.traverse { stage =>
              val signature = stage.signature
              val variants = stage.modelVariants.traverse { m =>
                versions
                  .get(m.modelVersion.id)
                  .toRight(UsingModelVersionIsMissing(ar, adapterGraph.asLeft))
                  .map(Variant(_, m.weight))
              }
              variants.map(PipelineStage(_, signature))
            }
          } yield Application.Failed(ar.status_message) -> mappedStages
        case "Ready" =>
          val mappedStages = jsonGraph.as[ServableGraphAdapter] match {
            case Left(_) => // old application recovery logic
              IncompatibleExecutionGraphError(ar).asLeft
            case Right(adapterGraph) =>
              adapterGraph.stages.traverse { stage =>
                val variants = stage.modelVariants.traverse { s =>
                  for {
                    servable <- servables.get(s.item).toRight(UsingServableIsMissing(ar, s.item))
                    r <- servable.status match {
                      case Servable.Status.Serving =>
                        Variant(servable, s.weight).asRight
                      case _ => IncompatibleExecutionGraphError(ar).asLeft
                    }
                    version <-
                      versions
                        .get(r.item.modelVersion.id)
                        .toRight(UsingModelVersionIsMissing(ar, adapterGraph.asRight))
                    v = Variant(version, s.weight)
                  } yield v -> r
                }
                variants.map { lst =>
                  val vars     = lst.map(_._2)
                  val versions = lst.map(_._1)
                  PipelineStage(versions, stage.signature) -> ExecutionNode(vars, stage.signature)
                }
              }
          }
          mappedStages.map { stages =>
            val execGraph = stages.map(_._2)
            val verGraph  = stages.map(_._1)
            Application.Ready(execGraph) -> verGraph
          }
        case _ => InvalidAppStatus(ar).asLeft
      }
      kafkaStreaming <-
        ar.kafka_streams.traverse(p => parse(p).flatMap(_.as[ApplicationKafkaStream]))
      metadata =
        ar.metadata
          .flatMap(str => parse(str).flatMap(_.as[Map[String, String]]).toOption)
          .getOrElse(Map.empty)
    } yield Application(
      id = ar.id,
      name = ar.application_name,
      signature = ar.application_contract,
      kafkaStreaming = kafkaStreaming,
      namespace = ar.namespace,
      status = status,
      versionGraph = graph,
      metadata = metadata
    )

  def fromApplication(app: GenericApplication): ApplicationRow = {
    val (status, msg, servables, versions, graph) = app.status match {
      case Application.Assembling =>
        val versionsIdx = app.versionGraph.flatMap(_.modelVariants.map(_.item.id)).toList
        (
          "Assembling",
          None,
          List.empty,
          versionsIdx,
          ExecutionGraphAdapter.fromVersionPipeline(app.versionGraph)
        )
      case Application.Failed(reason) =>
        val versionsIdx = app.versionGraph.flatMap(_.modelVariants.map(_.item.id)).toList
        (
          "Failed",
          reason,
          List.empty,
          versionsIdx,
          ExecutionGraphAdapter.fromVersionPipeline(app.versionGraph)
        )
      case Application.Ready(servableGraph) =>
        val adapter     = ExecutionGraphAdapter.fromServablePipeline(servableGraph)
        val versionsIdx = app.versionGraph.flatMap(_.modelVariants.map(_.item.id)).toList
        val servables   = adapter.stages.flatMap(_.modelVariants.map(_.item))
        ("Ready", None, servables.toList, versionsIdx, adapter)
    }
    ApplicationRow(
      id = app.id,
      application_name = app.name,
      namespace = app.namespace,
      status = status,
      application_contract = app.signature,
      execution_graph = graph.asJson.noSpaces,
      used_servables = servables,
      used_model_versions = versions,
      kafka_streams = app.kafkaStreaming.map(_.asJson.noSpaces),
      status_message = msg,
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

  def make[F[_]]()(implicit F: Bracket[F, Throwable], tx: Transactor[F]): ApplicationRepository[F] =
    new ApplicationRepository[F] {
      override def create(entity: GenericApplication): F[GenericApplication] = {
        val row = fromApplication(entity)
        for {
          id <- createQ(row).withUniqueGeneratedKeys[Long]("id").transact(tx)
        } yield entity.copy(id = id)
      }

      override def get(id: Long): F[Option[GenericApplication]] = {
        val result = for {
          app                   <- OptionT(getByIdQ(id).option.transact(tx))
          (versions, servables) <- OptionT.liftF(fetchAppInfo(tx, app))
          res                   <- OptionT.liftF(F.fromEither(toApplication(app, versions, servables)))
        } yield res
        result.value
      }

      override def get(name: String): F[Option[GenericApplication]] = {
        val result = for {
          app                   <- OptionT(getByNameQ(name).option.transact(tx))
          (versions, servables) <- OptionT.liftF(fetchAppInfo(tx, app))
          app                   <- OptionT.liftF(F.fromEither(toApplication(app, versions, servables)))
        } yield app
        result.value
      }

      override def update(value: GenericApplication): F[Int] = {
        val row = fromApplication(value)
        updateQ(row).run.transact(tx)
      }

      override def delete(id: Long): F[Int] =
        deleteQ(id).run.transact(tx)

      override def all(): F[List[GenericApplication]] =
        for {
          apps                      <- allQ.to[List].transact(tx)
          (versionMap, servableMap) <- fetchAppsInfo(tx, apps)
          res                       <- F.fromEither(apps.traverse(app => toApplication(app, versionMap, servableMap)))
        } yield res

      override def findVersionUsage(versionId: Long): F[List[GenericApplication]] =
        for {
          apps                      <- modelVersionUsageQ(versionId).to[List].transact(tx)
          (versionMap, servableMap) <- fetchAppsInfo(tx, apps)
          res                       <- F.fromEither(apps.traverse(app => toApplication(app, versionMap, servableMap)))
        } yield res

      override def findServableUsage(servableName: String): F[List[GenericApplication]] =
        for {
          apps                      <- servableUsageQ(servableName).to[List].transact(tx)
          (versionMap, servableMap) <- fetchAppsInfo(tx, apps)
          res                       <- F.fromEither(apps.traverse(app => toApplication(app, versionMap, servableMap)))
        } yield res

      override def updateRow(row: ApplicationRow): F[Int] =
        updateQ(row).run.transact(tx)
    }

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
      versions <- {
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
          case None => F.pure(Nil)
        }
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
          case None => F.pure(Nil)
        }

      versionMap  = versions.map(x => x.id -> x).toMap
      servableMap = servables.map(x => x.fullName -> x).toMap
    } yield versionMap -> servableMap

}
