package io.hydrosphere.serving.manager.infrastructure.db.repository

import cats.data.OptionT
import cats.effect.Async
import cats.implicits._
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.db.Tables
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.application.Application.GenericApplication
import io.hydrosphere.serving.manager.domain.application._
import io.hydrosphere.serving.manager.domain.application.graph.VersionGraphComposer.PipelineStage
import io.hydrosphere.serving.manager.domain.application.graph.{ExecutionGraphAdapter, ExecutionNode, ServableGraphAdapter, Variant, VersionGraphAdapter}
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.hydrosphere.serving.manager.domain.servable.Servable.{GenericServable, OkServable}
import io.hydrosphere.serving.manager.infrastructure.db.DatabaseService
import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol
import io.hydrosphere.serving.manager.util.AsyncUtil
import org.apache.logging.log4j.scala.Logging
import spray.json._

import scala.concurrent.ExecutionContext

class DBApplicationRepository[F[_]: Async](
  implicit executionContext: ExecutionContext,
  databaseService: DatabaseService,
  servableDb: DBServableRepository[F]
) extends ApplicationRepository[F] with Logging with CompleteJsonProtocol {

  import DBApplicationRepository._
  import databaseService._
  import databaseService.driver.api._


  override def create(entity: GenericApplication): F[GenericApplication] = AsyncUtil.futureAsync {
    logger.debug(s"create ${entity}")
    val status = flatten(entity)
    val elem = Tables.ApplicationRow(
      id = entity.id,
      applicationName = entity.name,
      namespace = entity.namespace,
      status = status.status,
      applicationContract = entity.signature.toProtoString,
      executionGraph = status.graph.toJson.compactPrint,
      usedServables = status.usedServables,
      kafkaStreams = entity.kafkaStreaming.map(p => p.toJson.toString()),
      statusMessage = status.message
    )
    db.run(Tables.Application returning Tables.Application += elem)
      .map(s => entity.copy(id = s.id))
  }

  override def get(id: Long): F[Option[GenericApplication]] = {
    val f = for {
      appTable <- OptionT(AsyncUtil.futureAsync {
        logger.debug(s"get ${id}")
        db.run(
          Tables.Application
            .filter(_.id === id)
            .result.headOption
        )
      })
      servables <- OptionT.liftF(servableDb.get(appTable.usedServables))
      sMap = servables.map(x => x.fullName -> x).toMap
      app <- OptionT.fromOption[F](mapFromDb(appTable, sMap).toOption)
    } yield app
    f.value
  }

  override def delete(id: Long): F[Int] = AsyncUtil.futureAsync {
    db.run(
      Tables.Application
        .filter(_.id === id)
        .delete
    )
  }

  override def all(): F[List[GenericApplication]] = {
    for {
      appTable <- AsyncUtil.futureAsync(db.run(Tables.Application.result))
      servableNames = appTable.flatMap(_.usedServables)
      servables <- servableDb.get(servableNames)
      sMap = servables.map(x => x.fullName -> x).toMap
      apps = appTable
        .map(appT => mapFromDb(appT, sMap))
        .collect { case Right(v) => v }
    } yield apps.toList
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
      status.usedServables,
      value.kafkaStreaming.map(_.toJson.toString),
      value.namespace,
      value.signature.toProtoString,
      status.status,
      status.message
    )))
  }

  override def applicationsWithCommonServices(servables: Set[GenericServable], appId: Long): F[List[GenericApplication]] = {
    for {
      appTable <- AsyncUtil.futureAsync {
        db.run(
          Tables.Application
            .filter { p =>
              p.usedServables @> servables.map(_.fullName).toList && p.id =!= appId
            }
            .result
        )
      }
      sNames = appTable.flatMap(_.usedServables)
      servables <- servableDb.get(sNames)
      sMap = servables.map(x => x.fullName -> x).toMap
      apps = appTable
        .map(appT => mapFromDb(appT, sMap))
        .collect { case Right(v) => v }
    } yield apps.toList
  }

  override def findVersionsUsage(versionIdx: Long): F[List[GenericApplication]] = {
    for {
      appTable <- AsyncUtil.futureAsync {
        db.run {
          Tables.Application
            .filter(a => a.usedServables @> List(versionIdx.toString))
            .result
        }
      }
      sNames = appTable.flatMap(_.usedServables)
      servables <- servableDb.get(sNames)
      sMap = servables.map(x => x.fullName -> x).toMap
      apps = appTable
        .map(appT => mapFromDb(appT, sMap))
        .collect { case Right(v) => v }
    } yield apps.toList
  }

  override def get(name: String): F[Option[GenericApplication]] = {
    val f = for {
      appTable <- OptionT(AsyncUtil.futureAsync {
        db.run(
          Tables.Application
            .filter(_.applicationName === name)
            .result.headOption
        )
      })
      servables <- OptionT.liftF(servableDb.get(appTable.usedServables))
      sMap = servables.map(x => x.fullName -> x).toMap
      app <- OptionT.fromOption[F](mapFromDb(appTable, sMap).toOption)
    } yield app
    f.value
  }
}

object DBApplicationRepository extends CompleteJsonProtocol {

  import spray.json._

  def mapFromDb(dbType: Tables.Application#TableElementType, servables: Map[String, GenericServable]): Either[Throwable, Application[Application.Status]] = {
    compose(dbType.status, dbType.statusMessage, dbType.executionGraph, servables).map { status =>
      Application(
        id = dbType.id,
        name = dbType.applicationName,
        signature = ModelSignature.fromAscii(dbType.applicationContract),
        kafkaStreaming = dbType.kafkaStreams.map(p => p.parseJson.convertTo[ApplicationKafkaStream]),
        namespace = dbType.namespace,
        status = status
      )
    }
  }

  case class FlattenedStatus(status: String, message: Option[String], usedServables: List[String], graph: ExecutionGraphAdapter)

  def flatten(app: GenericApplication): FlattenedStatus = {
    app.status match {
      case Application.Assembling(versionGraph) =>
        FlattenedStatus("Assembling", None, List.empty, ExecutionGraphAdapter.fromVersionPipeline(versionGraph))
      case Application.Failed(versionGraph, reason) =>
        FlattenedStatus("Failed", reason, List.empty, ExecutionGraphAdapter.fromVersionPipeline(versionGraph))
      case Application.Ready(servableGraph) =>
        val adapter = ExecutionGraphAdapter.fromServablePipeline(servableGraph)
        val servables = adapter.stages.flatMap(_.modelVariants.map(_.item))
        FlattenedStatus("Ready", None, servables.toList, adapter)
    }
  }

  def compose(status: String, message: Option[String], graph: String, servables: Map[String, GenericServable]): Either[Throwable, Application.Status] = {
    status match {
      case "Assembling" =>
        val adapterGraph = graph.parseJson.convertTo[VersionGraphAdapter]
        val mappedStages = adapterGraph.stages.map { stage =>
          val signature = stage.signature
          val variants = stage.modelVariants.map(m => Variant(m.modelVersion, m.weight))
          PipelineStage(variants, signature)
        }
        Application.Assembling(mappedStages).asRight[Exception]
      case "Failed" =>
        val adapterGraph = graph.parseJson.convertTo[VersionGraphAdapter]
        val mappedStages = adapterGraph.stages.map { stage =>
          val signature = stage.signature
          val variants = stage.modelVariants.map(m => Variant(m.modelVersion, m.weight))
          PipelineStage(variants, signature)
        }
        Application.Failed(mappedStages, message).asRight[Exception]
      case "Ready" =>
        val adapterGraph = graph.parseJson.convertTo[ServableGraphAdapter]
        val mappedStages = adapterGraph.stages.traverse { stage =>
          val variants = stage.modelVariants.traverse { s =>
            servables.get(s.item)
              .toRight(new Exception(s"Can't find servable with name ${s.item}"))
              .flatMap { servable =>
                servable.status match {
                  case _: Servable.Serving =>
                    Variant(servable.asInstanceOf[OkServable], s.weight).asRight
                  case x =>
                    DomainError.internalError(s"Servable ${servable.fullName} has invalid status $x").asLeft
                }
              }
          }
          variants.map(ExecutionNode(_, stage.signature))
        }
        mappedStages.map(Application.Ready)
      case x => DomainError.internalError(s"Unknown application status: $x").asLeft
    }
  }
}