package io.hydrosphere.serving.manager.domain.application

import cats.implicits._
import cats.effect.implicits._
import cats.data.OptionT
import cats.effect.Concurrent
import cats.effect.concurrent.Deferred
import io.hydrosphere.serving.manager.discovery.application.ApplicationDiscoveryHub
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.DomainError.InvalidRequest
import io.hydrosphere.serving.manager.domain.application.Application.{Assembling, AssemblingApp, GenericApplication}
import io.hydrosphere.serving.manager.domain.application.ApplicationService.Internals
import io.hydrosphere.serving.manager.domain.application.graph.{ExecutionNode, Node, Variant, VersionGraphComposer}
import io.hydrosphere.serving.manager.domain.application.requests.ExecutionGraphRequest
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersionRepository, ModelVersionStatus}
import io.hydrosphere.serving.manager.domain.servable.Servable.OkServable
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableService}
import io.hydrosphere.serving.manager.util.DeferredResult
import org.apache.logging.log4j.scala.Logging

trait ApplicationDeployer[F[_]] {
  def deploy(
    name: String,
    executionGraph: ExecutionGraphRequest,
    kafkaStreaming: Option[List[ApplicationKafkaStream]]
  ): F[DeferredResult[F, GenericApplication]]
}

object ApplicationDeployer extends Logging {
  def default[F[_]](
    servableService: ServableService[F],
    versionRepository: ModelVersionRepository[F],
    applicationRepository: ApplicationRepository[F],
    graphComposer: VersionGraphComposer,
    discoveryHub: ApplicationDiscoveryHub[F]
  )(implicit F: Concurrent[F]): ApplicationDeployer[F] = {
    new ApplicationDeployer[F] {
      override def deploy(
        name: String,
        executionGraph: ExecutionGraphRequest,
        kafkaStreaming: Option[List[ApplicationKafkaStream]]
      ): F[DeferredResult[F, GenericApplication]] = {
        for {
          composedApp <- composeApp(name, None, executionGraph, kafkaStreaming)
          repoApp <- applicationRepository.create(composedApp)
          app = composedApp.copy(id = repoApp.id)
          df <- Deferred[F, GenericApplication]
          _ <- (for {
            genericApp <- startServices(app)
            _ <- applicationRepository.update(genericApp)
            translated = Internals.toServingApp(genericApp)
            _ <- discoveryHub.added(translated)
            _ <- df.complete(genericApp)
          } yield ())
            .handleErrorWith { x =>
              val failedApp = app.copy(status = Application.Failed(composedApp.status.versionGraph, Some(x.getMessage)))
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
        kafkaStreaming: Option[List[ApplicationKafkaStream]]
      ): F[Application[Assembling]] = {
        for {
          _ <- checkApplicationName(name)
          versions <- executionGraph.stages.traverse { f =>
            for {
              variants <- f.modelVariants.traverse { m =>
                for {
                  version <- OptionT(versionRepository.get(m.modelVersionId))
                    .map(Variant(_, m.weight))
                    .getOrElseF(F.raiseError(DomainError.notFound(s"Can't find modelversion $m")))
                  _ <- version.item.status match {
                    case ModelVersionStatus.Released => F.unit
                    case x =>
                      F.raiseError[Unit](DomainError.invalidRequest(s"Can't deploy non-released ModelVersion: ${version.item.fullName} - $x"))
                  }
                } yield version
              }
            } yield Node(variants)
          }
          graphOrError = graphComposer.compose(versions)
          graph <- F.fromEither(graphOrError)
        } yield
          Application(
            id = 0,
            name = name,
            namespace = namespace,
            signature = graph.pipelineSignature,
            kafkaStreaming = kafkaStreaming.getOrElse(List.empty),
            status = Application.Assembling(graph.stages)
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

      private def startServices(app: AssemblingApp) = {
        for {
          deployedServables <- app.status.versionGraph.traverse { stage =>
            for {
              variants <- stage.modelVariants.traverse { i =>
                for {
                  result <- servableService.deploy(i.item)
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
                } yield Variant(okServable, i.weight)
              }
            } yield ExecutionNode(variants, stage.signature)
          }
          finishedApp = app.copy(status = Application.Ready(deployedServables))
        } yield finishedApp
      }
    }
  }
}