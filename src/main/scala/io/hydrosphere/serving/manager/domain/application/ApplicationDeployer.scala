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
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionRepository, ModelVersionStatus}
import io.hydrosphere.serving.manager.domain.servable.Servable.OkServable
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableService}
import io.hydrosphere.serving.manager.util.DeferredResult
import org.apache.logging.log4j.scala.Logging

trait ApplicationDeployer[F[_]] {
  def deploy(
    name: String,
    executionGraph: ExecutionGraphRequest,
    kafkaStreaming: List[ApplicationKafkaStream]
  ): F[DeferredResult[F, Application]]
}

object ApplicationDeployer extends Logging {
  def default[F[_]]()(
    implicit
    F: Concurrent[F],
    servableService: ServableService[F],
    versionRepository: ModelVersionRepository[F],
    applicationRepository: ApplicationRepository[F],
    discoveryHub: ApplicationEvents.Publisher[F],
    deploymentConfigService: DeploymentConfigurationService[F]
  ): ApplicationDeployer[F] = {
    new ApplicationDeployer[F] {
      override def deploy(
        name: String,
        executionGraph: ExecutionGraphRequest,
        kafkaStreaming: List[ApplicationKafkaStream]
      ): F[DeferredResult[F, Application]] = {
        for {
          composedApp <- composeApp(name, None, executionGraph, kafkaStreaming)
          repoApp <- applicationRepository.create(composedApp)
          _ <- discoveryHub.update(repoApp)
          app = composedApp.copy(id = repoApp.id)
          df <- Deferred[F, Application]
          _ <- (for {
            genericApp <- startServices(app)
            _ <- F.delay(logger.debug("App services started. All ok."))
            _ <- applicationRepository.update(genericApp)
            _ <- discoveryHub.update(genericApp)
            _ <- df.complete(genericApp)
          } yield ())
            .handleErrorWith { x =>
              val failedApp = app.copy(status = Application.Failed, statusMessage = Option(x.getMessage))
              F.delay(logger.error(s"Error while buidling application $failedApp", x)) >>
                applicationRepository.update(failedApp) >>
                df.complete(failedApp).attempt.void
            }
            .start
        } yield DeferredResult(app, df)
      }

      def composeApp(
        name: String,
        namespace: Option[String],
        executionGraph: ExecutionGraphRequest,
        kafkaStreaming: List[ApplicationKafkaStream]
      ): F[Application] = {
        for {
          _ <- checkApplicationName(name)
          versions <- executionGraph.stages.traverse { f =>
            for {
              variants <- f.modelVariants.traverse { m =>
                for {
                  version <- OptionT(versionRepository.get(m.modelVersionId))
                    .getOrElseF(F.raiseError(DomainError.notFound(s"Can't find modelversion $m")))
                  internalVersion <- version match {
                    case imv:  ModelVersion.Internal =>
                      imv.pure[F]
                    case emv:  ModelVersion.External =>
                      DomainError.invalidRequest(s"Can't deploy external ModelVersion ${emv.fullName}").raiseError[F,  ModelVersion.Internal]
                  }
                  _ <- internalVersion.status match {
                    case ModelVersionStatus.Released => F.unit
                    case x =>
                      F.raiseError[Unit](DomainError.invalidRequest(s"Can't deploy non-released ModelVersion: ${version.fullName} - $x"))
                  }
                  deploymentConfig <- m.deploymentConfigName.traverse(deploymentConfigService.get)
                } yield {
                  ApplicationServable(
                    modelVersion = internalVersion,
                    weight = m.weight,
                    requiredDeploymentConfig = deploymentConfig,
                    servable = None
                  )
                }
              }
            } yield variants
          }
          graphOrError <- F.fromEither(GraphComposer.compose(versions))
          (graph, contract) = graphOrError
        } yield
          Application(
            id = 0,
            name = name,
            namespace = namespace,
            signature = contract,
            kafkaStreaming = kafkaStreaming,
            status = Application.Assembling,
            statusMessage = None,
            graph = graph
          )
      }

      def checkApplicationName(name: String): F[String] = {
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
      }

      private def startServices(app: Application) = {
        for {
          deployedStages <- app.graph.stages.traverse { stage =>
            for {
              variants <- stage.variants.traverse { i =>
                for {
                  _ <- F.delay(logger.debug(s"Deploying ${i.modelVersion.fullName}"))
                  result <- servableService.deploy(i.modelVersion, i.requiredDeploymentConfig, Map.empty)  // NOTE maybe infer some app-specific labels?
                  servable <- result.completed.get
                  okServable <- servable.status match {
                    case _: Servable.Serving =>
                      servable.asInstanceOf[OkServable].pure[F]
                    case Servable.NotServing(msg, _, _) =>
                      F.raiseError[OkServable](DomainError.internalError(s"Servable ${servable.fullName} is in invalid state: $msg"))
                    case Servable.NotAvailable(msg, _, _) =>
                      F.raiseError[OkServable](DomainError.internalError(s"Servable ${servable.fullName} is in invalid state: $msg"))
                    case Servable.Starting(msg, _, _) =>
                      F.raiseError[OkServable](DomainError.internalError(s"Servable ${servable.fullName} is in invalid state: $msg"))
                  }
                } yield i.copy(servable = okServable.some)
              }
            } yield ApplicationStage(variants, stage.signature)
          }
          finishedApp = app.copy(status = Application.Ready, graph = ApplicationGraph(deployedStages))
        } yield finishedApp
      }
    }
  }
}