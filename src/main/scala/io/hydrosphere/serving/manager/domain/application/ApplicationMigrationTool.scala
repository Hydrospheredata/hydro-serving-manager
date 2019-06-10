package io.hydrosphere.serving.manager.domain.application

import cats.MonadError
import cats.data.OptionT
import cats.implicits._
import io.hydrosphere.serving.manager.domain.application.Application.GenericApplication
import io.hydrosphere.serving.manager.domain.application.graph.VersionGraphAdapter
import io.hydrosphere.serving.manager.domain.application.requests.{ExecutionGraphRequest, ModelVariantRequest, PipelineStageRequest}
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBApplicationRepository.IncompatibleExecutionGraphError
import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol
import org.apache.logging.log4j.scala.Logging
import spray.json._

trait ApplicationMigrationTool[F[_]] {
  def getAndRevive(): F[List[GenericApplication]]
}

object ApplicationMigrationTool extends Logging with CompleteJsonProtocol {
  def default[F[_]](
    appsRepo: ApplicationRepository[F],
    cloudDriver: CloudDriver[F],
    appDeployer: ApplicationDeployer[F]
  )(implicit F: MonadError[F, Throwable]): ApplicationMigrationTool[F] = new ApplicationMigrationTool[F] {
    override def getAndRevive(): F[List[GenericApplication]] = {
      for {
        maybeApps <- appsRepo.all().attempt
        apps <- maybeApps match {
          case Left(value) =>
            logger.warn("Encountered application db errors. Trying to recover.", value)

            val appsToRestore = value match {
              case IncompatibleExecutionGraphError(dbApp) => List(dbApp)
              case x => x.getSuppressed.toList.collect {
                case IncompatibleExecutionGraphError(dbApp) => dbApp
              }
            }
            logger.debug(appsToRestore)
            appsToRestore.traverse { rawApp =>
              val oldGraph = rawApp.executionGraph.parseJson.convertTo[VersionGraphAdapter]
              for {
                _ <- oldGraph.stages.traverse { stage =>
                  stage.modelVariants.traverse { variant =>
                    logger.debug(s"Cleaning old ${variant}")
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
                streaming = rawApp.kafkaStreams.map(p => p.parseJson.convertTo[ApplicationKafkaStream])
                _ = logger.debug(s"Restoring ${rawApp.applicationName}")
                newApp <- appDeployer.deploy(rawApp.applicationName, graph, streaming)
              } yield newApp.started
            }
          case Right(value) =>
            logger.info("Applications are ok.")
            value.pure[F]
        }
      } yield apps
    }
  }
}