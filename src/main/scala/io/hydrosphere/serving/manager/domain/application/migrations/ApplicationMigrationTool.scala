package io.hydrosphere.serving.manager.domain.application.migrations

import cats.data.OptionT
import cats.effect.Bracket
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.domain.application._
import io.hydrosphere.serving.manager.domain.application.requests.{ExecutionGraphRequest, ModelVariantRequest, PipelineStageRequest}
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionRepository}
import io.hydrosphere.serving.manager.domain.servable.Servable.GenericServable
import io.hydrosphere.serving.manager.domain.servable.ServableRepository
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBApplicationRepository
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBApplicationRepository._
import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol
import org.apache.logging.log4j.scala.Logging
import spray.json._

import scala.util.Try

trait ApplicationMigrationTool[F[_]] {
  def getAndRevive(): F[Unit]
}

object ApplicationMigrationTool extends Logging with CompleteJsonProtocol {
  sealed trait AppDBSchemaError extends Throwable with Serializable with Product
  case class IncompatibleExecutionGraphError(app: ApplicationRow) extends AppDBSchemaError
  case class UsingModelVersionIsMissing(app: ApplicationRow, graph: Either[VersionGraphAdapter, ServableGraphAdapter]) extends AppDBSchemaError
  case class UsingServableIsMissing(app: ApplicationRow, servableName: String) extends AppDBSchemaError
  case class InvalidApplicationSchema(app: ApplicationRow, ex: Option[Throwable] = None) extends AppDBSchemaError {
    override def getMessage: String = s"${ex.map(_.getMessage)} happened for ${app}"
  }

  def tryConvertVersionAdapter(ar: ApplicationRow, versions: Map[Long, ModelVersion.Internal]): Either[AppDBSchemaError, ApplicationGraph] = {
    Try(ar.execution_graph.parseJson.convertTo[VersionGraphAdapter])
      .toEither
      .leftMap(e => InvalidApplicationSchema(ar, e.some))
      .flatMap { adapterGraph =>
        val mappedStages = adapterGraph.stages.traverse { stage =>
          val signature = stage.signature
          val variants = stage.modelVariants.traverse { m =>
            versions.get(m.modelVersion.id)
              .toRight(UsingModelVersionIsMissing(ar, adapterGraph.asLeft))
              .map(ApplicationServable(_, m.weight, None, None))
          }
          variants.map(ApplicationStage(_, signature))
        }
        mappedStages.map(ApplicationGraph.apply)
      }
  }

  def tryConvertExecutionAdapter(ar: ApplicationRow, versions: Map[Long, ModelVersion.Internal], servables: Map[String, GenericServable]): Either[AppDBSchemaError, ApplicationGraph] = {
    Try(ar.execution_graph.parseJson.convertTo[ServableGraphAdapter])
      .toEither
      .leftMap(e => InvalidApplicationSchema(ar, e.some))
      .flatMap { adapterGraph =>
        val stages = adapterGraph.stages.traverse { stage =>
          val variants = stage.modelVariants.traverse { s =>
            for {
              servable <- servables.get(s.item).toRight(UsingServableIsMissing(ar, s.item))
              version <- versions.get(servable.modelVersion.id).toRight(UsingModelVersionIsMissing(ar, adapterGraph.asRight))
              v = ApplicationServable(version, s.weight, servable.some, None)
            } yield v
          }
          variants.map(ApplicationStage(_, stage.signature))
        }
        stages.map(ApplicationGraph.apply)
      }
  }

  def makeAppFromOldAdapter(ar: ApplicationRow, versions: Map[Long, ModelVersion.Internal], servables: Map[String, GenericServable]): Either[AppDBSchemaError, Application] = {
    DBApplicationRepository.toApplication(ar, versions, servables, Map.empty) match {
      case Left(value) =>
        logger.debug(value)
        for {
          statusAndGraph <- ar.status match {
            case "Assembling" =>
              tryConvertVersionAdapter(ar, versions).map(Application.Assembling -> _)
            case "Failed" =>
              tryConvertVersionAdapter(ar, versions).map(Application.Failed -> _)
            case "Ready" =>
              tryConvertExecutionAdapter(ar, versions, servables).map(Application.Ready -> _)
            case _ => InvalidApplicationSchema(ar).asLeft
          }
        } yield Application(
          id = ar.id,
          name = ar.application_name,
          signature = ModelSignature.fromAscii(ar.application_contract),
          kafkaStreaming = ar.kafka_streams.map(p => p.parseJson.convertTo[ApplicationKafkaStream]),
          namespace = ar.namespace,
          status = statusAndGraph._1,
          statusMessage = ar.status_message,
          graph = statusAndGraph._2,
          metadata = ar.metadata.map(_.parseJson.convertTo[Map[String, String]]).getOrElse(Map.empty)
        )
      case Right(value) =>
        value.asRight
    }

  }

  def default[F[_]](
    appsRepo: ApplicationRepository[F],
    modelVersionRepo: ModelVersionRepository[F],
    cloudDriver: CloudDriver[F],
    appDeployer: ApplicationDeployer[F],
    servableRepository: ServableRepository[F],
  )(implicit F: Bracket[F, Throwable], tx: Transactor[F]): ApplicationMigrationTool[F] = new ApplicationMigrationTool[F] {
    override def getAndRevive() = {
      for {
        rawApps <- DBApplicationRepository.allQ.to[List].transact(tx)
        result <- rawApps.traverse { appRow =>
          for {
            versions <- appRow.used_model_versions
              .traverse(modelVersionRepo.get)
              .map {
                _.collect {
                  case Some(x: ModelVersion.Internal) => x.id -> x
                }.toMap
              }
            servables <- appRow.used_servables
              .traverse(servableRepository.get)
              .map {
                _.collect {
                  case Some(x) => x.fullName -> x
                }.toMap
              }
            _ <- makeAppFromOldAdapter(appRow, versions, servables) match {
              case Right(x) => appsRepo.update(x).void
              case Left(IncompatibleExecutionGraphError(app)) => restoreServables(app).void
              case Left(UsingModelVersionIsMissing(app, graph)) => restoreVersions(app, graph).void
              case Left(UsingServableIsMissing(app, _)) => restoreServables(app).void
              case Left(err) => F.raiseError[Unit](err)
            }
          } yield ()
        }.attempt
        _ <- result match {
          case Right(_) =>
            logger.info("Applications are ok.")
            F.unit
          case Left(err) =>
            logger.error("Unrecoverable error while reading Application from database", err)
            err.raiseError[F, Unit]
        }
      } yield ()
    }

    def restoreVersions(rawApp: ApplicationRow, graph: Either[VersionGraphAdapter, ServableGraphAdapter]) = {
      for {
        newApp <- graph match {
          case Left(value) =>
            val usedVersions = value.stages.flatMap(_.modelVariants.map(_.modelVersion.id)).toList
            rawApp.copy(used_model_versions = usedVersions).pure[F]

          case Right(value) =>
            val servableNames = value.stages.flatMap(_.modelVariants.map(_.item)).toList
            for {
              servables <- servableRepository.get(servableNames)
              versions = servables.map(_.modelVersion.id)
              newApp = rawApp.copy(used_model_versions = versions)
            } yield newApp
        }
        _ <- DBApplicationRepository.updateQ(newApp).run.transact(tx).void
      } yield newApp
    }

    def restoreServables(rawApp: ApplicationRow) = {
      val oldGraph = rawApp.execution_graph.parseJson.convertTo[VersionGraphAdapter]
      for {
        _ <- oldGraph.stages.traverse { stage =>
          stage.modelVariants.traverse { variant =>
            logger.debug(s"Cleaning old $variant")
            val x = for {
              instance <- OptionT(cloudDriver.getByVersionId(variant.modelVersion.id))
              _ <- OptionT.liftF(cloudDriver.remove(instance.name))
            } yield instance
            x.value
          }.void
        }
        _ = logger.debug(s"Deleting app ${rawApp.id}")
        _ <- appsRepo.delete(rawApp.id)
        graph = ExecutionGraphRequest(
          oldGraph.stages.map { stage =>
            PipelineStageRequest(
              stage.modelVariants.map { mv =>
                ModelVariantRequest(
                  modelVersionId = mv.modelVersion.id,
                  weight = mv.weight
                )
              }
            )
          }
        )
        streaming = rawApp.kafka_streams.map(p => p.parseJson.convertTo[ApplicationKafkaStream])
        _ = logger.debug(s"Restoring ${rawApp.application_name}")
        newApp <- appDeployer.deploy(rawApp.application_name, graph, streaming)
      } yield rawApp
    }
  }
}