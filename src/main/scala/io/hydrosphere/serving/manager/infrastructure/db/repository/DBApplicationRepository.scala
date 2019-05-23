package io.hydrosphere.serving.manager.infrastructure.db.repository

import cats.effect.Async
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.db.Tables
import io.hydrosphere.serving.manager.domain.application.Application.GenericApplication
import io.hydrosphere.serving.manager.domain.application._
import io.hydrosphere.serving.manager.domain.application.graph.ExecutionGraphAdapter
import io.hydrosphere.serving.manager.domain.servable.Servable.GenericServable
import io.hydrosphere.serving.manager.infrastructure.db.DatabaseService
import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol
import io.hydrosphere.serving.manager.util.AsyncUtil
import org.apache.logging.log4j.scala.Logging
import spray.json._

import scala.concurrent.ExecutionContext

class DBApplicationRepository[F[_]: Async](
  implicit val executionContext: ExecutionContext,
  databaseService: DatabaseService
) extends ApplicationRepository[F] with Logging with CompleteJsonProtocol {

  import DBApplicationRepository._
  import databaseService._
  import databaseService.driver.api._

  override def create(entity: GenericApplication): F[GenericApplication] = AsyncUtil.futureAsync {
    val status = flatten(entity)
    val elem = Tables.ApplicationRow(
      id = entity.id,
      applicationName = entity.name,
      namespace = entity.namespace,
      status = status.status,
      applicationContract = entity.signature.toProtoString,
      executionGraph = status.graph.toJson.compactPrint,
      usedServables = List.empty,
      kafkaStreams = entity.kafkaStreaming.map(p => p.toJson.toString()),
      statusMessage = status.message
    )
    db.run(Tables.Application returning Tables.Application += elem)
      .map(s => mapFromDb(s))
  }

  override def get(id: Long): F[Option[GenericApplication]] = AsyncUtil.futureAsync {
    db.run(
      Tables.Application
        .filter(_.id === id)
        .result.headOption
    ).map(s => mapFromDb(s))
  }

  override def delete(id: Long): F[Int] = AsyncUtil.futureAsync {
    db.run(
      Tables.Application
        .filter(_.id === id)
        .delete
    )
  }

  override def all(): F[List[GenericApplication]] = AsyncUtil.futureAsync {
    db.run(
      Tables.Application
        .result
    ).map(s => s.map(ss => mapFromDb(ss)).toList)
  }

  override def update(value: GenericApplication): F[Int] = AsyncUtil.futureAsync {
    val query = for {
      serv <- Tables.Application if serv.id === value.id
    } yield (
      serv.applicationName,
      serv.executionGraph,
      serv.usedServables,
      serv.kafkaStreams,
      serv.namespace,
      serv.applicationContract,
      serv.status,
      serv.statusMessage
    )
    val status = flatten(value)
    db.run(query.update((
      value.name,
      status.graph.toJson.compactPrint,
      List.empty,
      value.kafkaStreaming.map(_.toJson.toString),
      value.namespace,
      value.signature.toProtoString,
      status.status,
      status.message
    )))
  }

  override def applicationsWithCommonServices(servables: Set[GenericServable], applicationId: Long): F[Seq[GenericApplication]] = AsyncUtil.futureAsync {
    db.run(
      Tables.Application
        .filter { p =>
          p.usedServables @> servables.map(_.fullName).toList && p.id =!= applicationId
        }
        .result
    ).map(s => s.map(mapFromDb))
  }

  override def findVersionsUsage(versionIdx: Long): F[Seq[GenericApplication]] = AsyncUtil.futureAsync {
    db.run {
      Tables.Application
        .filter(a => a.usedServables @> List(versionIdx.toString))
        .result
    }.map(_.map(mapFromDb))
  }

  override def get(name: String): F[Option[GenericApplication]] = AsyncUtil.futureAsync {
    db.run(
      Tables.Application
        .filter(_.applicationName === name)
        .result.headOption
    ).map(s => mapFromDb(s))
  }
}

object DBApplicationRepository extends CompleteJsonProtocol {

  import spray.json._

  def mapFromDb(dbType: Option[Tables.Application#TableElementType]): Option[GenericApplication] =
    dbType.map(r => mapFromDb(r))

  def mapFromDb(dbType: Tables.Application#TableElementType): GenericApplication = {
    Application(
      id = dbType.id,
      name = dbType.applicationName,
      signature = ModelSignature.fromAscii(dbType.applicationContract),
      kafkaStreaming = dbType.kafkaStreams.map(p => p.parseJson.convertTo[ApplicationKafkaStream]),
      namespace = dbType.namespace,
      status = dbType.executionGraph.parseJson.convertTo[Application.Status]
    )
  }

  case class FlattenedStatus(status: String, message: Option[String], graph: ExecutionGraphAdapter)

  def flatten(app: GenericApplication) = {
    app.status match {
      case Application.Assembling(versionGraph) =>
        FlattenedStatus("Assembling", None, ExecutionGraphAdapter.fromVersionPipeline(versionGraph))
      case Application.Failed(versionGraph, reason) =>
        FlattenedStatus("Failed", reason, ExecutionGraphAdapter.fromVersionPipeline(versionGraph))
      case Application.Ready(servableGraph) =>
        FlattenedStatus("Ready", None, ExecutionGraphAdapter.fromServablePipeline(servableGraph))
    }
  }
}