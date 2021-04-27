package io.hydrosphere.serving.manager.domain.application

import cats.data.OptionT
import cats.effect.Concurrent
import cats.effect.concurrent.Deferred
import cats.effect.implicits._
import cats.implicits._
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.DomainError.InvalidRequest
import io.hydrosphere.serving.manager.domain.application.requests.ExecutionGraphRequest
import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfigurationService
import io.hydrosphere.serving.manager.domain.model_version.{
  ModelVersion,
  ModelVersionRepository,
  ModelVersionStatus
}
import io.hydrosphere.serving.manager.domain.monitoring.{Monitoring, MonitoringRepository}
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableService}
import io.hydrosphere.serving.manager.util.DeferredResult
import org.apache.logging.log4j.scala.Logging

trait ApplicationDeployer[F[_]] {
  def deploy(
      name: String,
      executionGraph: ExecutionGraphRequest,
      kafkaStreaming: List[ApplicationKafkaStream],
      metadata: Map[String, String]
  ): F[Application]
}

object ApplicationDeployer extends Logging {
  def default[F[_]]()(implicit
      F: Concurrent[F],
      servableService: ServableService[F],
      versionRepository: ModelVersionRepository[F],
      applicationRepository: ApplicationRepository[F],
      deploymentConfigService: DeploymentConfigurationService[F],
      monitoringRepo: MonitoringRepository[F],
      monitoringService: Monitoring[F]
  ): ApplicationDeployer[F] = {
    new ApplicationDeployer[F] {
      override def deploy(
          name: String,
          executionGraph: ExecutionGraphRequest,
          kafkaStreaming: List[ApplicationKafkaStream],
          metadata: Map[String, String]
      ): F[Application] =
        for {
          composedApp <- composeApp(name, None, executionGraph, kafkaStreaming, metadata)
          repoApp     <- applicationRepository.create(composedApp)
          app = composedApp.copy(id = repoApp.id)
          _ <- startServices(app).void.start
        } yield app

      def composeApp(
          name: String,
          namespace: Option[String],
          executionGraph: ExecutionGraphRequest,
          kafkaStreaming: List[ApplicationKafkaStream],
          metadata: Map[String, String]
      ): F[Application] =
        for {
          _ <- checkApplicationName(name)
          versions <- executionGraph.stages.traverse { f =>
            for {
              variants <- f.modelVariants.traverse { m =>
                for {
                  version <- OptionT(versionRepository.get(m.modelVersionId))
                    .getOrElseF(F.raiseError(DomainError.notFound(s"Can't find modelversion $m")))
                  internalVersion <- version match {
                    case imv: ModelVersion.Internal =>
                      imv.pure[F]
                    case emv: ModelVersion.External =>
                      DomainError
                        .invalidRequest(s"Can't deploy external ModelVersion ${emv.fullName}")
                        .raiseError[F, ModelVersion.Internal]
                  }
                  _ <- internalVersion.status match {
                    case ModelVersionStatus.Released => F.unit
                    case x =>
                      F.raiseError[Unit](
                        DomainError.invalidRequest(
                          s"Can't deploy non-released ModelVersion: ${version.fullName} - $x"
                        )
                      )
                  }
                  deploymentConfig <- m.deploymentConfigName.traverse(deploymentConfigService.get)
                } yield ApplicationServable(
                  modelVersion = internalVersion,
                  weight = m.weight,
                  requiredDeploymentConfig = deploymentConfig,
                  servable = None
                )
              }
            } yield variants
          }
          graphOrError <- F.fromEither(GraphComposer.compose(versions))
          (graph, contract) = graphOrError
        } yield Application(
          id = 0,
          name = name,
          namespace = namespace,
          signature = contract,
          kafkaStreaming = kafkaStreaming,
          graph = graph,
          metadata = metadata
        )

      def checkApplicationName(name: String): F[String] =
        for {
          _ <- ApplicationValidator.name(name) match {
            case Some(_) => F.unit
            case None =>
              F.raiseError[Unit](
                InvalidRequest(
                  s"Application name $name contains invalid symbols. It should only contain latin letters, numbers '-' and '_'"
                )
              )
          }
          maybeApp <- applicationRepository.get(name)
          _ <- maybeApp match {
            case Some(_) =>
              F.raiseError[Unit](InvalidRequest(s"Application with name $name already exists"))
            case None => F.unit
          }
        } yield name

      private def startServices(app: Application) = {
        val servableMetadata = Map(
          "applicationName" -> app.name,
          "applicationId"   -> app.id.toString
        )
        for {
          deployedStages <- app.graph.stages.traverse { stage =>
            for {
              variants <- stage.variants.traverse { i =>
                for {
                  _         <- F.delay(logger.debug(s"Deploying ${i.modelVersion.fullName}"))
                  mvMetrics <- monitoringRepo.forModelVersion(i.modelVersion.id)
                  newMetricServables <-
                    mvMetrics
                      .filter(_.config.servable.isEmpty)
                      .traverse(x => monitoringService.deployServable(x))
                  _ <- F.delay(logger.debug(s"Deployed MetricServables: ${newMetricServables}"))
                  servable <- servableService.deploy(
                    i.modelVersion,
                    i.requiredDeploymentConfig,
                    servableMetadata
                  ) // NOTE maybe infer some app-specific labels?
                } yield i.copy(servable = servable.some)
              }
            } yield ApplicationStage(variants, stage.signature)
          }
          finishedApp = app.copy(graph = ApplicationGraph(deployedStages))
          _ <- applicationRepository.update(finishedApp)
        } yield finishedApp
      }
    }
  }
}
