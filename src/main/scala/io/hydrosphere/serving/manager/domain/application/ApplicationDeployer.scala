package io.hydrosphere.serving.manager.domain.application

import cats.data.OptionT
import cats.effect.Concurrent
import cats.effect.concurrent.Deferred
import cats.effect.implicits._
import cats.implicits._
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.DomainError.InvalidRequest
import io.hydrosphere.serving.manager.domain.application.Application.{
  AssemblingApp,
  GenericApplication
}
import io.hydrosphere.serving.manager.domain.application.graph.{
  ExecutionNode,
  Node,
  Variant,
  VersionGraphComposer
}
import io.hydrosphere.serving.manager.domain.application.requests.ExecutionGraphRequest
import io.hydrosphere.serving.manager.domain.model_version.{
  ModelVersion,
  ModelVersionRepository,
  ModelVersionStatus
}
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableService}
import io.hydrosphere.serving.manager.util.{DeferredResult, UnsafeLogging}

trait ApplicationDeployer[F[_]] {
  def deploy(
      name: String,
      executionGraph: ExecutionGraphRequest,
      kafkaStreaming: List[ApplicationKafkaStream]
  ): F[DeferredResult[F, GenericApplication]]
}

object ApplicationDeployer extends UnsafeLogging {
  def default[F[_]]()(implicit
      F: Concurrent[F],
      servableService: ServableService[F],
      versionRepository: ModelVersionRepository[F],
      applicationRepository: ApplicationRepository[F],
      graphComposer: VersionGraphComposer,
      discoveryHub: ApplicationEvents.Publisher[F]
  ): ApplicationDeployer[F] = {
    new ApplicationDeployer[F] {
      override def deploy(
          name: String,
          executionGraph: ExecutionGraphRequest,
          kafkaStreaming: List[ApplicationKafkaStream]
      ): F[DeferredResult[F, GenericApplication]] =
        for {
          composedApp <- composeApp(name, None, executionGraph, kafkaStreaming)
          repoApp     <- applicationRepository.create(composedApp)
          _           <- discoveryHub.update(repoApp)
          app = composedApp.copy(id = repoApp.id)
          df <- Deferred[F, GenericApplication]
          _ <- (for {
              genericApp <- startServices(app)
              _          <- F.delay(logger.debug("App services started. All ok."))
              _          <- applicationRepository.update(genericApp)
              _          <- discoveryHub.update(genericApp)
              _          <- df.complete(genericApp)
            } yield ()).handleErrorWith { x =>
            val failedApp = app.copy(status = Application.Failed(Option(x.getMessage)))
            F.delay(logger.error(s"Error while buidling application $failedApp", x)) >>
              applicationRepository.update(failedApp) >>
              df.complete(failedApp).attempt.void
          }.start
        } yield DeferredResult(app, df)

      def composeApp(
          name: String,
          namespace: Option[String],
          executionGraph: ExecutionGraphRequest,
          kafkaStreaming: List[ApplicationKafkaStream]
      ): F[AssemblingApp] =
        for {
          _ <- checkApplicationName(name)
          versions <- executionGraph.stages.traverse { f =>
            for {
              variants <- f.modelVariants.traverse { m =>
                for {
                  version <- OptionT(versionRepository.get(m.modelVersionId))
                    .map(Variant(_, m.weight))
                    .getOrElseF(F.raiseError(DomainError.notFound(s"Can't find modelversion $m")))
                  internalVersion <- version.item match {
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
                          s"Can't deploy non-released ModelVersion: ${version.item.fullName} - $x"
                        )
                      )
                  }
                } yield version.copy(item = internalVersion)
              }
            } yield Node(variants)
          }
          graphOrError = graphComposer.compose(versions)
          graph <- F.fromEither(graphOrError)
        } yield Application(
          id = 0,
          name = name,
          namespace = namespace,
          signature = graph.pipelineSignature,
          kafkaStreaming = kafkaStreaming,
          status = Application.Assembling,
          versionGraph = graph.stages
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

      private def startServices(app: AssemblingApp) =
        for {
          deployedServables <- app.versionGraph.traverse { stage =>
            for {
              variants <- stage.modelVariants.traverse { i =>
                for {
                  _ <- F.delay(logger.debug(s"Deploying ${i.item.fullName}"))
                  result <-
                    servableService
                      .deploy(i.item, Map.empty) // NOTE maybe infer some app-specific labels?
                  servable <- result.completed.get
                  okServable <- servable.status match {
                    case Servable.Status.Serving => servable.pure[F]
                    case Servable.Status.NotServing =>
                      F.raiseError[Servable](
                        DomainError
                          .internalError(
                            s"Servable ${servable.fullName} is in invalid state: ${servable.msg}"
                          )
                      )
                    case Servable.Status.NotAvailable =>
                      F.raiseError[Servable](
                        DomainError
                          .internalError(
                            s"Servable ${servable.fullName} is in invalid state: ${servable.msg}"
                          )
                      )
                    case Servable.Status.Starting =>
                      F.raiseError[Servable](
                        DomainError
                          .internalError(
                            s"Servable ${servable.fullName} is in invalid state: ${servable.msg}"
                          )
                      )
                  }
                } yield Variant(okServable, i.weight)
              }
            } yield ExecutionNode(variants, stage.signature)
          }
          finishedApp = app.copy(status = Application.Ready(deployedServables))
        } yield finishedApp
    }
  }
}
