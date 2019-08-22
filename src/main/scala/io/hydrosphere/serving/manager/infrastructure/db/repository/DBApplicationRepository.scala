package io.hydrosphere.serving.manager.infrastructure.db.repository

import cats.data.OptionT
import cats.effect.Bracket
import cats.free.Free
import cats.implicits._
import doobie.implicits._
import doobie._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.domain.application.Application.GenericApplication
import io.hydrosphere.serving.manager.domain.application._
import io.hydrosphere.serving.manager.domain.application.graph.VersionGraphComposer.PipelineStage
import io.hydrosphere.serving.manager.domain.application.graph._
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.hydrosphere.serving.manager.domain.servable.Servable.{GenericServable, OkServable}
import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol._
import spray.json._

import scala.util.{Failure, Success, Try}
import cats.data.NonEmptyList

object DBApplicationRepository {

  val tableName = "hydro_serving.application"

  final case class ApplicationRow(
    id: Long,
    application_name: String,
    namespace: Option[String],
    status: String,
    application_contract: String,
    execution_graph: String,
    kafka_streams: List[String],
    status_message: Option[String],
    used_model_versions: List[Long],
    used_servables: List[String],
  )

  def toApplication(ar: ApplicationRow, versions: Map[Long, ModelVersion], servables: Map[String, GenericServable]): Either[AppDBSchemaError, GenericApplication] = {
    val jsonGraph = ar.execution_graph.parseJson
    for {
      statusAndGraph <- ar.status match {
        case "Assembling" =>
          val adapterGraph = jsonGraph.convertTo[VersionGraphAdapter]
          val mappedStages = adapterGraph.stages.traverse { stage =>
            val signature = stage.signature
            val variants = stage.modelVariants.traverse { m =>
              versions.get(m.modelVersion.id)
                .toRight(UsingModelVersionIsMissing(ar, adapterGraph.asLeft))
                .map(Variant(_, m.weight))
            }
            variants.map(PipelineStage(_, signature))
          }
          mappedStages.map(x => Application.Assembling -> x)
        case "Failed" =>
          val adapterGraph = jsonGraph.convertTo[VersionGraphAdapter]
          val mappedStages = adapterGraph.stages.traverse { stage =>
            val signature = stage.signature
            val variants = stage.modelVariants.traverse { m =>
              versions.get(m.modelVersion.id)
                .toRight(UsingModelVersionIsMissing(ar, adapterGraph.asLeft))
                .map(Variant(_, m.weight))
            }
            variants.map(PipelineStage(_, signature))
          }
          mappedStages.map(x => Application.Failed(ar.status_message) -> x)
        case "Ready" =>
          val mappedStages = Try(jsonGraph.convertTo[ServableGraphAdapter]) match {
            case Failure(exception) => // old application recovery logic
              IncompatibleExecutionGraphError(ar).asLeft
            case Success(adapterGraph) =>
              adapterGraph.stages.traverse { stage =>
                val variants = stage.modelVariants.traverse { s =>
                  for {
                    servable <- servables.get(s.item).toRight(UsingServableIsMissing(ar, s.item))
                    r <- servable.status match {
                      case _: Servable.Serving => Variant(servable.asInstanceOf[OkServable], s.weight).asRight
                      case _ => IncompatibleExecutionGraphError(ar).asLeft
                    }
                    version <- versions.get(r.item.modelVersion.id).toRight(UsingModelVersionIsMissing(ar, adapterGraph.asRight))
                    v = Variant(version, s.weight)
                  } yield v -> r
                }
                variants.map { lst =>
                  val vars = lst.map(_._2)
                  val versions = lst.map(_._1)
                  PipelineStage(versions, stage.signature) -> ExecutionNode(vars, stage.signature)
                }
              }
          }
          mappedStages.map { stages =>
            val execGraph = stages.map(_._2)
            val verGraph = stages.map(_._1)
            Application.Ready(execGraph) -> verGraph
          }
        case _ => InvalidAppStatus(ar).asLeft
      }
    } yield Application(
      id = ar.id,
      name = ar.application_name,
      signature = ModelSignature.fromAscii(ar.application_contract),
      kafkaStreaming = ar.kafka_streams.map(p => p.parseJson.convertTo[ApplicationKafkaStream]),
      namespace = ar.namespace,
      status = statusAndGraph._1,
      versionGraph = statusAndGraph._2
    )
  }

  def fromApplication(app: GenericApplication): ApplicationRow = {
    val (status, msg, servables, versions, graph) = app.status match {
      case Application.Assembling =>
        val versionsIdx = app.versionGraph.flatMap(_.modelVariants.map(_.item.id)).toList
        ("Assembling", None, List.empty, versionsIdx, ExecutionGraphAdapter.fromVersionPipeline(app.versionGraph))
      case Application.Failed(reason) =>
        val versionsIdx = app.versionGraph.flatMap(_.modelVariants.map(_.item.id)).toList
        ("Failed", reason, List.empty, versionsIdx, ExecutionGraphAdapter.fromVersionPipeline(app.versionGraph))
      case Application.Ready(servableGraph) =>
        val adapter = ExecutionGraphAdapter.fromServablePipeline(servableGraph)
        val versionsIdx = app.versionGraph.flatMap(_.modelVariants.map(_.item.id)).toList
        val servables = adapter.stages.flatMap(_.modelVariants.map(_.item))
        ("Ready", None, servables.toList, versionsIdx, adapter)
    }
    ApplicationRow(
      id = app.id,
      application_name = app.name,
      namespace = app.namespace,
      status = status,
      application_contract = app.signature.toProtoString,
      execution_graph = graph.toJson.compactPrint,
      used_servables = servables,
      used_model_versions = versions,
      kafka_streams = app.kafkaStreaming.map(_.toJson.compactPrint),
      status_message = msg
    )
  }

  case class AppDBSchemaErrors(errs: List[AppDBSchemaError]) extends Throwable

  sealed trait AppDBSchemaError extends Throwable with Serializable with Product

  case class IncompatibleExecutionGraphError(app: ApplicationRow) extends AppDBSchemaError

  case class UsingModelVersionIsMissing(app: ApplicationRow, graph: Either[VersionGraphAdapter, ServableGraphAdapter]) extends AppDBSchemaError

  case class UsingServableIsMissing(app: ApplicationRow, servableName: String) extends AppDBSchemaError

  case class InvalidAppStatus(app: ApplicationRow) extends AppDBSchemaError

  def allQ =
    sql"""
         |SELECT * FROM $tableName
        """.stripMargin.query[ApplicationRow]

  def getByNameQ(name: String) =
    sql"""
          |SELECT * FROM $tableName
          | WHERE application_name = $name
      """.stripMargin.query[ApplicationRow]

  def getByIdQ(id: Long) =
    sql"""
         |SELECT * FROM $tableName
         | WHERE id = $id
      """.stripMargin.query[ApplicationRow]

  def modelVersionUsageQ(versionId: Long) =
    sql"""
          |SELECT * FROM $tableName
          | WHERE ${versionId} = ANY(used_model_versions)
      """.stripMargin.query[ApplicationRow]


  def servableUsageQ(servableName: String) =
    sql"""
         |SELECT * FROM $tableName
         | WHERE ${servableName} = ANY(used_servables)
      """.stripMargin.query[ApplicationRow]

  def createQ(app:ApplicationRow) =
    sql"""
          |INSERT INTO $tableName(
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
          |)
      """.stripMargin.update

  def updateQ(app: ApplicationRow) =
    sql"""
          |UPDATE TABLE $tableName SET
          | application_name = ${app.application_name},
          | used_servables = ${app.used_servables},
          | used_model_versions = ${app.used_model_versions},
          | application_contract = ${app.application_contract},
          | execution_graph = ${app.execution_graph},
          | kafka_streams = ${app.kafka_streams},
          | status = ${app.status},
          | status_message = ${app.status_message}
          | WHERE id = ${app.id}
      """.stripMargin.update

  def deleteQ(id: Long) =
    sql"""
          |DELETE FROM $tableName
          | WHERE id = $id
      """.stripMargin.update


  def make[F[_]]()(implicit F: Bracket[F, Throwable], tx: Transactor[F]): ApplicationRepository[F] = new ApplicationRepository[F] {
    override def create(entity: GenericApplication): F[GenericApplication] = {
      val row = fromApplication(entity)
      for {
        id <- createQ(row).withUniqueGeneratedKeys[Long]("id").transact(tx)
      } yield entity.copy(id = id)
    }

    override def get(id: Long): F[Option[GenericApplication]] = {
      val transaction = for {
        app <- OptionT(getByIdQ(id).option)
        appInfo <- OptionT.liftF(fetchAppInfo(app))
        (versions, servables) = appInfo
      } yield (app, versions, servables)
      val result = transaction.transact(tx).flatMap { case (app, versions, servables) =>
        OptionT.liftF(F.fromEither(toApplication(app, versions, servables)))
      }
      result.value
    }

    override def get(name: String): F[Option[GenericApplication]] = {
      val transaction = for {
        app <- OptionT(getByNameQ(name).option)
        appInfo <- OptionT.liftF(fetchAppInfo(app))
        (versions, servables) = appInfo
      } yield (app, versions, servables)
      val result = transaction.transact(tx).flatMap { case (app, versions, servables) =>
        OptionT.liftF(F.fromEither(toApplication(app, versions, servables)))
      }
      result.value
    }

    override def update(value: GenericApplication): F[Int] = {
      val row = fromApplication(value)
      updateQ(row).run.transact(tx)
    }

    override def delete(id: Long): F[Int] = {
      deleteQ(id).run.transact(tx)
    }

    override def all(): F[List[GenericApplication]] = {
      val t = for {
        apps <- allQ.to[List]
        info <- fetchAppsInfo(apps)
        (versionMap, servableMap) = info
        res = apps.traverse { app =>
          toApplication(app, versionMap, servableMap)
        }
      } yield res
      t.transact(tx).map(_.leftWiden[Throwable]).rethrow
    }

    override def findVersionUsage(versionId: Long): F[List[GenericApplication]] = {
      val t = for {
        apps <- modelVersionUsageQ(versionId).to[List]
        info <- fetchAppsInfo(apps)
        (versionMap, servableMap) = info
        res = apps.traverse { app =>
          toApplication(app, versionMap, servableMap)
        }
      } yield res
      t.transact(tx).map(_.leftWiden[Throwable]).rethrow
    }

    override def findServableUsage(servableName: String): F[List[GenericApplication]] = {
      val t = for {
        apps <- servableUsageQ(servableName).to[List]
        info <- fetchAppsInfo(apps)
        (versionMap, servableMap) = info
        res = apps.traverse { app =>
          toApplication(app, versionMap, servableMap)
        }
      } yield res
      t.transact(tx).map(_.leftWiden[Throwable]).rethrow
    }

    override def updateRow(row: ApplicationRow): F[Int] = {
      updateQ(row).run.transact(tx)
    }
  }

  def fetchAppInfo(app: ApplicationRow) = fetchAppsInfo(app :: Nil)

  def fetchAppsInfo(apps: List[ApplicationRow]) = {
   for {
     versions <- DBModelVersionRepository.findVersionsQ(apps.flatMap(_.used_model_versions)).to[List]
     versionMap = versions.map(x => x._1.model_version_id -> DBModelVersionRepository.toModelVersionT(x)).toMap
     servables <- {
       val allServables = apps.flatMap(_.used_servables)
       NonEmptyList.fromList(allServables) match {
         case Some(x) => DBServableRepository.getManyQ(x).to[List]
         case None => Nil.pure[ConnectionIO]
       }
     }
     servableMap = servables.map(x => x._1.service_name -> DBServableRepository.toServableT(x)).toMap
   } yield versionMap -> servableMap
  }

}