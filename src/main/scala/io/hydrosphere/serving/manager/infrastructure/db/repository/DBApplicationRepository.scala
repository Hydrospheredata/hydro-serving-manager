package io.hydrosphere.serving.manager.infrastructure.db.repository

import cats.data.{NonEmptyList, OptionT}
import cats.effect.Bracket
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.domain.application._
import io.hydrosphere.serving.manager.domain.application.graph._
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol._
import io.hydrosphere.serving.manager.util.CollectionOps._
import spray.json._

import scala.util.Try

object DBApplicationRepository {
  final case class ApplicationRow(
    id: Long,
    application_name: String,
    namespace: Option[String],
    status: String,
    application_contract: String,
    execution_graph: String,
    used_servables: List[String],
    kafka_streams: List[String],
    status_message: Option[String],
    used_model_versions: List[Long],
    metadata: Option[String],
    graph_nodes: List[String],
    graph_links: List[String]
  )

  def toApplication(ar: ApplicationRow, versions: Map[Long, ModelVersion], servables: Map[String, Servable]): Try[Application] = Try {
    val signature =  ModelSignature.fromAscii(ar.application_contract)
    val graph = ExecutionGraph(
      nodes = ar.graph_nodes.map(_.parseJson.convertTo[ExecutionGraph.ExecutionNode]),
      links = ar.graph_links.map(_.parseJson.convertTo[ExecutionGraph.DirectionalLink]),
      signature = signature
    )
    val status = ar.status.toLowerCase match {
      case "healthy" => Application.Healthy
      case "unhealthy" => Application.Unhealthy(ar.status_message)
    }
    val streamingParams = ar.kafka_streams.map(p => p.parseJson.convertTo[Application.KafkaParams])
    val metadata = ar.metadata.map(_.parseJson.convertTo[Map[String, String]]).getOrElse(Map.empty)
    Application(
      id = ar.id,
      name = ar.application_name,
      kafkaStreaming = streamingParams,
      status = status,
      graph = graph,
      metadata = metadata
    )
  }

  def fromApplication(app: Application): ApplicationRow = {
    val versionsIdx: List[Long] = ???
    val servables: List[String] = ???
    val (status, msg) = app.status match {
      case Application.Healthy =>
        ("Healthy", None)
      case Application.Unhealthy(reason) =>
        ("Unhealthy", reason)
    }
    val nodes = app.graph.nodes.map(_.toJson.compactPrint)
    val links = app.graph.links.map(_.toJson.compactPrint)
    val contract = app.graph.signature.toProtoString
    val metadata = app.metadata.maybeEmpty.map(_.toJson.compactPrint)
    val kafkaParams = app.kafkaStreaming.map(_.toJson.compactPrint)
    ApplicationRow(
      id = app.id,
      application_name = app.name,
      namespace = None,
      status = status,
      application_contract = contract,
      execution_graph = "",
      used_servables = servables,
      used_model_versions = versionsIdx,
      kafka_streams = kafkaParams,
      status_message = msg,
      metadata = metadata,
      graph_links = links,
      graph_nodes = nodes
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
         |SELECT * FROM hydro_serving.application
        """.stripMargin.query[ApplicationRow]

  def getByNameQ(name: String) =
    sql"""
          |SELECT * FROM hydro_serving.application
          | WHERE application_name = $name
      """.stripMargin.query[ApplicationRow]

  def getByIdQ(id: Long) =
    sql"""
         |SELECT * FROM hydro_serving.application
         | WHERE id = $id
      """.stripMargin.query[ApplicationRow]

  def modelVersionUsageQ(versionId: Long) =
    sql"""
          |SELECT * FROM hydro_serving.application
          | WHERE ${versionId} = ANY(used_model_versions)
      """.stripMargin.query[ApplicationRow]


  def servableUsageQ(servableName: String) =
    sql"""
         |SELECT * FROM hydro_serving.application
         | WHERE ${servableName} = ANY(used_servables)
      """.stripMargin.query[ApplicationRow]

  def createQ(app:ApplicationRow) =
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
          |)
      """.stripMargin.update

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
          | WHERE id = ${app.id}
      """.stripMargin.update

  def deleteQ(id: Long) =
    sql"""
          |DELETE FROM hydro_serving.application
          | WHERE id = $id
      """.stripMargin.update


  def make[F[_]]()(implicit F: Bracket[F, Throwable], tx: Transactor[F]): ApplicationRepository[F] = new ApplicationRepository[F] {
    override def create(entity: Application): F[Application] = {
      val row = fromApplication(entity)
      for {
        id <- createQ(row).withUniqueGeneratedKeys[Long]("id").transact(tx)
      } yield entity.copy(id = id)
    }

    override def get(id: Long): F[Option[Application]] = {
      val transaction = for {
        app <- OptionT(getByIdQ(id).option)
        appInfo <- OptionT.liftF(fetchAppInfo(app))
        (versions, servables) = appInfo
      } yield (app, versions, servables)
      val result = transaction.transact(tx).flatMap { case (app, versions, servables) =>
        OptionT.liftF(F.fromTry(toApplication(app, versions, servables)))
      }
      result.value
    }

    override def get(name: String): F[Option[Application]] = {
      val transaction = for {
        app <- OptionT(getByNameQ(name).option)
        appInfo <- OptionT.liftF(fetchAppInfo(app))
        (versions, servables) = appInfo
      } yield (app, versions, servables)
      val result = transaction.transact(tx).flatMap { case (app, versions, servables) =>
        OptionT.liftF(F.fromTry(toApplication(app, versions, servables)))
      }
      result.value
    }

    override def update(value: Application): F[Int] = {
      val row = fromApplication(value)
      updateQ(row).run.transact(tx)
    }

    override def delete(id: Long): F[Int] = {
      deleteQ(id).run.transact(tx)
    }

    override def all(): F[List[Application]] = {
      val t = for {
        apps <- allQ.to[List]
        info <- fetchAppsInfo(apps)
        (versionMap, servableMap) = info
        res = apps.traverse { app =>
          toApplication(app, versionMap, servableMap)
        }
      } yield res.toEither
      t.transact(tx).rethrow
    }

    override def findVersionUsage(versionId: Long): F[List[Application]] = {
      val t = for {
        apps <- modelVersionUsageQ(versionId).to[List]
        info <- fetchAppsInfo(apps)
        (versionMap, servableMap) = info
        res = apps.traverse { app =>
          toApplication(app, versionMap, servableMap)
        }
      } yield res.toEither
      t.transact(tx).rethrow
    }

    override def findServableUsage(servableName: String): F[List[Application]] = {
      val t = for {
        apps <- servableUsageQ(servableName).to[List]
        info <- fetchAppsInfo(apps)
        (versionMap, servableMap) = info
        res = apps.traverse { app =>
          toApplication(app, versionMap, servableMap)
        }
      } yield res.toEither
      t.transact(tx).rethrow
    }

    override def updateRow(row: ApplicationRow): F[Int] = {
      updateQ(row).run.transact(tx)
    }
  }

  def fetchAppInfo(app: ApplicationRow) = fetchAppsInfo(app :: Nil)

  def fetchAppsInfo(apps: List[ApplicationRow]) = {
   for {
     versions <- {
       val allVersions = apps.flatMap(_.used_model_versions)
       NonEmptyList.fromList(allVersions) match {
         case Some(x) => DBModelVersionRepository.findVersionsQ(x).to[List]
         case None => Nil.pure[ConnectionIO]
       }
     }
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