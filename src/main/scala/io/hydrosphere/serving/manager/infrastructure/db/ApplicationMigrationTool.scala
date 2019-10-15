package io.hydrosphere.serving.manager.infrastructure.db

import cats.data.OptionT
import cats.effect.Bracket
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.domain.application.graph._
import io.hydrosphere.serving.manager.domain.application.requests._
import io.hydrosphere.serving.manager.domain.application._
import io.hydrosphere.serving.manager.domain.application.graph.compat.{ServableGraphAdapter, VersionGraphAdapter}
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.domain.servable.ServableRepository
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBApplicationRepository
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBApplicationRepository._
import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol
import org.apache.logging.log4j.scala.Logging
import spray.json._

trait ApplicationMigrationTool[F[_]] {
  def getAndRevive(): F[Unit]
}

object ApplicationMigrationTool extends Logging with CompleteJsonProtocol {
  def default[F[_]](
    appsRepo: ApplicationRepository[F],
    cloudDriver: CloudDriver[F],
    appDeployer: ApplicationDeployer[F],
    servableRepository: ServableRepository[F],
  )(implicit F: Bracket[F, Throwable]): ApplicationMigrationTool[F] = new ApplicationMigrationTool[F] {
    override def getAndRevive() = {
      for {
        maybeApps <- appsRepo.all().attempt
        _ <- maybeApps match {
          case Left(AppDBSchemaErrors(errors)) =>
            logger.warn(s"Encountered application db schema errors. Trying to recover.\n${errors.mkString("\n")}")
            errors.traverse {
              case IncompatibleExecutionGraphError(dbApp) => restoreServables(dbApp).void
              case UsingModelVersionIsMissing(dbApp, graph) => restoreVersions(dbApp, graph).void
              case err =>
                logger.error("Can't recover following error", err)
                F.unit
            }.void
          case Left(err) =>
            logger.error("Can't recover from this db schema error", err)
            err.raiseError[F, Unit]
          case Right(_) =>
            logger.info("Applications are ok.")
            F.unit
        }
      } yield ()
    }

    def restoreVersions(rawApp: ApplicationRow, graph: Either[VersionGraphAdapter, ServableGraphAdapter]) = {
      val fixedApp = graph match {
        case Left(value) =>
          val usedVersions = value.stages.flatMap(_.modelVariants.map(_.modelVersion.id)).toList
          rawApp.copy(used_model_versions = usedVersions).pure[F]

        case Right(value) =>
          val servableNames = value.stages.flatMap(_.modelVariants.map(_.item)).toList
          for {
            servables <- servableRepository.get(servableNames)
            versions = servables.map(_.modelVersion.id)
            newApp = rawApp.copy(used_model_versions = versions, used_servables = servables.map(_.fullName))
          } yield newApp
      }
      for {
        newApp <- fixedApp
        _ <- appsRepo.updateRow(newApp)
      } yield newApp
    }

    def restoreServables(rawApp: ApplicationRow) = {
      val oldGraph = rawApp.execution_graph.parseJson.convertTo[VersionGraphAdapter]
      for {
        _ <- appsRepo.delete(rawApp.id)
        _ =  logger.debug(s"Deleted app ${rawApp.id}")
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
        streaming = rawApp.kafka_streams.map(p => p.parseJson.convertTo[Application.KafkaParams])
        _ = logger.debug(s"Restoring ${rawApp.application_name}")
        newApp <- appDeployer.deploy(rawApp.application_name, graph, streaming)
      } yield rawApp
    }
  }
}