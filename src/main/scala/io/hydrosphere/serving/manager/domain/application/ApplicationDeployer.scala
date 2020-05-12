package io.hydrosphere.serving.manager.domain.application

import cats.data.OptionT
import cats.effect.Concurrent
import cats.effect.concurrent.Deferred
import cats.effect.implicits._
import cats.implicits._
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.DomainError.InvalidRequest
import io.hydrosphere.serving.manager.domain.application.graph.VersionGraphComposer
import io.hydrosphere.serving.manager.domain.application.requests.ExecutionGraphRequest
import io.hydrosphere.serving.manager.domain.model_version._
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableService}
import io.hydrosphere.serving.manager.util.{DeferredResult, UnsafeLogging}

trait ApplicationDeployer[F[_]] {
  def deploy(
      name: String,
      executionGraph: ExecutionGraphRequest,
      kafkaStreaming: List[ApplicationKafkaStream]
  ): F[DeferredResult[F, Application]]
}

object ApplicationDeployer extends UnsafeLogging {
  def default[F[_]](
      servableService: ServableService[F],
      versionRepository: ModelVersionRepository[F],
      applicationRepository: ApplicationRepository[F],
      discoveryHub: ApplicationEvents.Publisher[F]
  )(implicit
      F: Concurrent[F]
  ): ApplicationDeployer[F] = {
    new ApplicationDeployer[F] {
      override def deploy(
          name: String,
          executionGraph: ExecutionGraphRequest,
          kafkaStreaming: List[ApplicationKafkaStream]
      ): F[DeferredResult[F, Application]] =
        for {
          composedApp <- composeApp(name, None, executionGraph, kafkaStreaming)
          repoApp     <- applicationRepository.create(composedApp)
          _           <- discoveryHub.update(repoApp)
          app = composedApp.copy(id = repoApp.id)
          df <- Deferred[F, Application]
          _ <- (for {
              genericApp <- startServices(app)
              _          <- F.delay(logger.debug("App services started. All ok."))
              _          <- applicationRepository.update(genericApp)
              _          <- discoveryHub.update(genericApp)
              _          <- df.complete(genericApp)
            } yield ()).handleErrorWith { x =>
            val failedApp = app.copy(
              status = Application.Status.Failed,
              message = Option(x.getMessage).getOrElse(s"No exception message for ${x.getClass}")
            )
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
                      Variant(imv, None, m.weight).pure[F]
                    case emv: ModelVersion.External =>
                      DomainError
                        .invalidRequest(s"Can't deploy external ModelVersion ${emv.fullName}")
                        .raiseError[F, Variant]
                  }
                  _ <- internalVersion.modelVersion.status match {
                    case ModelVersionStatus.Released =>
                      F.unit
                    case x =>
                      DomainError
                        .invalidRequest(
                          s"Can't deploy non-released ModelVersion: ${internalVersion.modelVersion.fullName} - $x"
                        )
                        .raiseError[F, Unit]
                  }
                } yield internalVersion
              }
            } yield variants
          }
          graph <- F.fromEither(VersionGraphComposer.compose(versions))
        } yield Application(
          id = 0,
          name = name,
          namespace = namespace,
          kafkaStreaming = kafkaStreaming,
          status = Application.Status.Assembling,
          executionGraph = graph,
          message = "Application is waiting for underlying Servables"
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

      private def startServices(app: Application) =
        for {
          deployedServables <- app.executionGraph.nodes.traverse { stage =>
            for {
              variants <- stage.variants.traverse { i =>
                for {
                  _ <- F.delay(logger.debug(s"Deploying ${i.modelVersion.fullName}"))
                  result <-
                    servableService
                      .deploy(
                        i.modelVersion,
                        Map.empty
                      ) // NOTE maybe infer some app-specific labels?
                  servable <- result.completed.get
                  okServable <- servable.status match {
                    case Servable.Status.Serving => servable.pure[F]
                    case Servable.Status.NotServing =>
                      F.raiseError[Servable](
                        DomainError
                          .internalError(
                            s"Servable ${servable.fullName} is in invalid state: ${servable.message}"
                          )
                      )
                    case Servable.Status.NotAvailable =>
                      F.raiseError[Servable](
                        DomainError
                          .internalError(
                            s"Servable ${servable.fullName} is in invalid state: ${servable.message}"
                          )
                      )
                    case Servable.Status.Starting =>
                      F.raiseError[Servable](
                        DomainError
                          .internalError(
                            s"Servable ${servable.fullName} is in invalid state: ${servable.message}"
                          )
                      )
                  }
                } yield Variant(i.modelVersion, okServable.some, i.weight)
              }
            } yield WeightedNode(variants, stage.signature)
          }
          finishedApp = app.copy(
            status = Application.Status.Ready,
            executionGraph = ApplicationGraph(deployedServables, app.executionGraph.signature)
          )
        } yield finishedApp
    }
  }
}
