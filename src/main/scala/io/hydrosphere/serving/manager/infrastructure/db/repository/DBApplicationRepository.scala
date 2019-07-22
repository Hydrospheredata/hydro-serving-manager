package io.hydrosphere.serving.manager.infrastructure.db.repository

import cats.effect.Bracket
import cats.implicits._
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

//class DBApplicationRepository[F[_]](
//  implicit F: Async[F],
//  tx: Transactor[F],
//  servableDb: DBServableRepository[F],
//  versionDb: DBModelVersionRepository[F]
//) extends ApplicationRepository[F] with Logging with CompleteJsonProtocol {
//
//  import DBApplicationRepository._
//  import databaseService._
//  import databaseService.driver.api._
//
//  override def create(entity: GenericApplication): F[GenericApplication] = {
//    for {
//      table <- AsyncUtil.futureAsync {
//        val status = flatten(entity)
//        val elem = Tables.ApplicationRow(
//          id = entity.id,
//          applicationName = entity.name,
//          namespace = entity.namespace,
//          status = status.status,
//          applicationContract = entity.signature.toProtoString,
//          executionGraph = status.graph.toJson.compactPrint,
//          usedServables = status.usedServables,
//          kafkaStreams = entity.kafkaStreaming.map(p => p.toJson.toString()),
//          statusMessage = status.message,
//          usedModelVersions = status.usedVersions
//        )
//        db.run(Tables.Application returning Tables.Application += elem)
//      }
//      app = entity.copy(id = table.id)
//      _ = logger.debug(s"create $app")
//    } yield app
//  }
//
//  override def get(id: Long): F[Option[GenericApplication]] = {
//    val f = for {
//      appTable <- OptionT(AsyncUtil.futureAsync {
//        logger.debug(s"get $id")
//        db.run(
//          Tables.Application
//            .filter(_.id === id)
//            .result.headOption
//        )
//      })
//      servables <- OptionT.liftF(servableDb.get(appTable.usedServables))
//      sMap = servables.map(x => x.fullName -> x).toMap
//      versions <-OptionT.liftF(versionDb.get(appTable.usedModelVersions))
//      vMap = versions.map(x => x.id -> x).toMap
//      app <- OptionT.liftF(F.fromEither(mapFromDb(appTable, vMap, sMap)))
//    } yield app
//    f.value
//  }
//
//  override def delete(id: Long): F[Int] = AsyncUtil.futureAsync {
//    db.run(
//      Tables.Application
//        .filter(_.id === id)
//        .delete
//    )
//  }
//
//  override def all(): F[List[GenericApplication]] = {
//    for {
//      appTable <- AsyncUtil.futureAsync(db.run(Tables.Application.result))
//      servables <- servableDb.all()
//      sMap = servables.map(x => x.fullName -> x).toMap
//      versions <- versionDb.get(appTable.flatMap(_.usedModelVersions))
//      vMap = versions.map(x => x.id -> x).toMap
//      apps = appTable.toList
//        .traverse(appT => mapFromDb(appT, vMap, sMap).toValidatedNec)
//        .leftMap { errors =>
//          errors.map(x => logger.error("db retrieval error", x))
//          val err = AppDBSchemaErrors(errors.toList)
//          err
//        }
//      f <- F.fromValidated(apps)
//    } yield f
//  }
//
//  override def update(value: GenericApplication): F[Int] = AsyncUtil.futureAsync {
//    logger.debug(s"update $value")
//    val query = for {
//      application <- Tables.Application if application.id === value.id
//    } yield (
//      application.applicationName,
//      application.executionGraph,
//      application.usedServables,
//      application.usedModelVersions,
//      application.kafkaStreams,
//      application.namespace,
//      application.applicationContract,
//      application.status,
//      application.statusMessage
//    )
//    val status = flatten(value)
//    db.run(query.update((
//      value.name,
//      status.graph.toJson.compactPrint,
//      status.usedServables,
//      status.usedVersions,
//      value.kafkaStreaming.map(_.toJson.toString),
//      value.namespace,
//      value.signature.toProtoString,
//      status.status,
//      status.message
//    )))
//  }
//
//  def updateRow(app: ApplicationRow): F[Int] = AsyncUtil.futureAsync {
//    db.run(Tables.Application.insertOrUpdate(app))
//  }
//
//  override def applicationsWithCommonServices(servables: Set[GenericServable], appId: Long): F[List[GenericApplication]] = {
//    for {
//      appTable <- AsyncUtil.futureAsync {
//        db.run(
//          Tables.Application
//            .filter { p =>
//              p.usedServables @> servables.map(_.fullName).toList && p.id =!= appId
//            }
//            .result
//        )
//      }
//      sNames = appTable.flatMap(_.usedServables)
//      servables <- servableDb.get(sNames)
//      sMap = servables.map(x => x.fullName -> x).toMap
//      versions <- versionDb.get(appTable.flatMap(_.usedModelVersions))
//      vMap = versions.map(x => x.id -> x).toMap
//      apps <- appTable.toList
//        .traverse(appT => F.fromEither(mapFromDb(appT, vMap, sMap)))
//    } yield apps
//  }
//
//  override def findVersionsUsage(versionIdx: Long): F[List[GenericApplication]] = {
//    for {
//      appTable <- AsyncUtil.futureAsync {
//        db.run {
//          Tables.Application
//            .filter(a => a.usedModelVersions @> List(versionIdx))
//            .result
//        }
//      }
//      sNames = appTable.flatMap(_.usedServables)
//      servables <- servableDb.get(sNames)
//      sMap = servables.map(x => x.fullName -> x).toMap
//      versions <- versionDb.get(appTable.flatMap(_.usedModelVersions))
//      vMap = versions.map(x => x.id -> x).toMap
//      apps <- appTable.toList
//        .traverse(appT => F.fromEither(mapFromDb(appT, vMap, sMap)))
//    } yield apps
//  }
//
//  override def get(name: String): F[Option[GenericApplication]] = {
//    val f = for {
//      appTable <- OptionT(AsyncUtil.futureAsync {
//        db.run(
//          Tables.Application
//            .filter(_.applicationName === name)
//            .result.headOption
//        )
//      })
//      servables <- OptionT.liftF(servableDb.get(appTable.usedServables))
//      sMap = servables.map(x => x.fullName -> x).toMap
//      versions <- OptionT.liftF(versionDb.get(appTable.usedModelVersions))
//      vMap = versions.map(x => x.id -> x).toMap
//      app <- OptionT.liftF(F.fromEither(mapFromDb(appTable,vMap, sMap)))
//    } yield app
//    f.value
//  }
//
//  override def findServableUsage(servableName: String): F[List[GenericApplication]] = {
//    for {
//      appTable <- db.task {
//          Tables.Application
//            .filter(a => a.usedServables @> List(servableName))
//            .result
//        }
//      sNames = appTable.flatMap(_.usedServables)
//      servables <- servableDb.get(sNames)
//      sMap = servables.map(x => x.fullName -> x).toMap
//      versions <- versionDb.get(appTable.flatMap(_.usedModelVersions))
//      vMap = versions.map(x => x.id -> x).toMap
//      apps <- appTable.toList
//        .traverse(appT => F.fromEither(mapFromDb(appT, vMap, sMap)))
//    } yield apps
//  }
//}

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
    used_model_versions: List[Long]
  )

  def toApplication(ar: ApplicationRow, versions: Map[Long, ModelVersion], servables: Map[String, GenericServable]): Either[AppDBSchemaError, GenericApplication]  = {
    val jsonGraph = ar.execution_graph.parseJson
    for {
      status <- ar.status match {
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
          mappedStages.map(Application.Assembling.apply)
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
          mappedStages.map(Application.Failed(_, ar.status_message))
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
                    _ <- versions.get(r.item.modelVersion.id).toRight(UsingModelVersionIsMissing(ar, adapterGraph.asRight))
                  } yield r
                }
                variants.map(ExecutionNode(_, stage.signature))
              }
          }
          mappedStages.map(Application.Ready)
        case _ => InvalidAppStatus(ar).asLeft
      }
    } yield Application(
      id = ar.id,
      name = ar.application_name,
      signature = ModelSignature.fromAscii(ar.application_contract),
      kafkaStreaming = ar.kafka_streams.map(p => p.parseJson.convertTo[ApplicationKafkaStream]),
      namespace = ar.namespace,
      status = status
    )
  }

  def fromApplication(app: GenericApplication): ApplicationRow = {
    val (status, msg, servables, versions, graph) = app.status match {
      case Application.Assembling(versionGraph) =>
        val versionsIdx = versionGraph.flatMap(_.modelVariants.map(_.item.id)).toList
        ("Assembling", None, List.empty, versionsIdx, ExecutionGraphAdapter.fromVersionPipeline(versionGraph))
      case Application.Failed(versionGraph, reason) =>
        val versionsIdx = versionGraph.flatMap(_.modelVariants.map(_.item.id)).toList
        ("Failed", reason, List.empty, versionsIdx, ExecutionGraphAdapter.fromVersionPipeline(versionGraph))
      case Application.Ready(servableGraph) =>
        val adapter = ExecutionGraphAdapter.fromServablePipeline(servableGraph)
        val versionsIdx = servableGraph.flatMap(_.variants.map(_.item.modelVersion.id)).toList
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

  sealed trait AppDBSchemaError extends Throwable

  case class IncompatibleExecutionGraphError(app: ApplicationRow) extends AppDBSchemaError

  case class UsingModelVersionIsMissing(app: ApplicationRow, graph: Either[VersionGraphAdapter, ServableGraphAdapter]) extends AppDBSchemaError

  case class UsingServableIsMissing(app: ApplicationRow, servableName: String) extends AppDBSchemaError

  case class InvalidAppStatus(app: ApplicationRow) extends AppDBSchemaError

  def make[F[_]](tx: Transactor[F])(implicit F: Bracket[F, Throwable]): ApplicationRepository[F] = new ApplicationRepository[F] {
    override def create(entity: GenericApplication): F[GenericApplication] = ???

    override def get(id: Long): F[Option[GenericApplication]] = ???

    override def get(name: String): F[Option[GenericApplication]] = ???

    override def update(value: GenericApplication): F[Int] = ???

    override def delete(id: Long): F[Int] = ???

    override def all(): F[List[GenericApplication]] = ???

    override def findVersionsUsage(versionIdx: Long): F[List[GenericApplication]] = ???

    override def findServableUsage(servableName: String): F[List[GenericApplication]] = ???
  }

}