package io.hydrosphere.serving.manager.infrastructure.db

import cats.data.OptionT
import cats.effect.Bracket
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.domain.application.graph.{ServableGraphAdapter, VersionGraphAdapter}
import io.hydrosphere.serving.manager.domain.application.requests.{ExecutionGraphRequest, ModelVariantRequest, PipelineStageRequest}
import io.hydrosphere.serving.manager.domain.application.{ApplicationDeployer, ApplicationKafkaStream, ApplicationRepository}
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.domain.servable.ServableRepository
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBApplicationRepository
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBApplicationRepository.{AppDBSchemaErrors, ApplicationRow, IncompatibleExecutionGraphError, UsingModelVersionIsMissing}
import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol
import io.hydrosphere.serving.manager.util.UnsafeLogging

trait ApplicationMigrationTool[F[_]] {
  def getAndRevive(): F[Unit]
}

object ApplicationMigrationTool extends UnsafeLogging with CompleteJsonProtocol {
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
            newApp = rawApp.copy(used_model_versions = versions)
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