package io.hydrosphere.serving.manager

import cats.data.OptionT
import cats.effect.Bracket
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.domain.application._
import io.hydrosphere.serving.manager.domain.application.graph._
import io.hydrosphere.serving.manager.domain.application.requests._
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.domain.servable.ServableRepository
import io.hydrosphere.serving.manager.infrastructure.db.FlywayClient
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBApplicationRepository
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBApplicationRepository._
import io.hydrosphere.serving.manager.util.UnsafeLogging
import io.hydrosphere.serving.manager.util.JsonOps._

trait DBStarter[F[_]] {
  def init(): F[Unit]
  def checkApplicationGraphs(): F[Unit]
}

object DBStarter extends UnsafeLogging {

  def make[F[_]](
      appsRepo: ApplicationRepository[F],
      cloudDriver: CloudDriver[F],
      appDeployer: ApplicationDeployer[F],
      servableRepository: ServableRepository[F],
      flyway: FlywayClient[F],
      tx: Transactor[F]
  )(implicit F: Bracket[F, Throwable]): DBStarter[F] =
    new DBStarter[F] {
      override def checkApplicationGraphs(): F[Unit] =
        appsRepo.all().attempt.flatMap {
          case Left(AppDBSchemaErrors(errors)) =>
            logger.warn(
              s"Encountered application db schema incompatibilities. Trying to recover.\n${errors.mkString("\n")}"
            )
            errors.traverse {
              case IncompatibleExecutionGraphError(dbApp)   => restoreServables(dbApp).void
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

      def restoreVersions(
          rawApp: ApplicationRow,
          graph: Either[VersionGraphAdapter, ServableGraphAdapter]
      ): F[ApplicationRow] = {
        val fixedApp = graph match {
          case Left(value) =>
            val usedVersions = value.stages.flatMap(_.modelVariants.map(_.modelVersion.id)).toList
            rawApp.copy(used_model_versions = usedVersions).pure[F]

          case Right(value) =>
            val servableNames = value.stages.flatMap(_.modelVariants.map(_.item)).toNes
            for {
              servables <- servableRepository.get(servableNames)
              versions = servables.map(_.modelVersion.id)
              newApp   = rawApp.copy(used_model_versions = versions)
            } yield newApp
        }
        for {
          newApp <- fixedApp
          _      <- DBApplicationRepository.updateQ(newApp).run.transact(tx)
        } yield newApp
      }

      def restoreServables(rawApp: ApplicationRow): F[ApplicationRow] =
        for {
          oldGraph <- F.fromEither(rawApp.execution_graph.parseJsonAs[VersionGraphAdapter])
          _ <- oldGraph.stages.traverse { stage =>
            stage.modelVariants.traverse { variant =>
              logger.debug(s"Cleaning old $variant")
              val x = for {
                instance <- OptionT(cloudDriver.getByVersionId(variant.modelVersion.id))
                _        <- OptionT.liftF(cloudDriver.remove(instance.name))
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
          streaming =
            rawApp.kafka_streams
              .map(p => p.parseJsonAs[ApplicationKafkaStream])
              .collect {
                case Right(value) => value
              }
          _ = logger.debug(s"Restoring ${rawApp.application_name}")
          _ <- appDeployer.deploy(rawApp.application_name, graph, streaming)
        } yield rawApp

      override def init(): F[Unit] =
        flyway.migrate().void
    }

}
