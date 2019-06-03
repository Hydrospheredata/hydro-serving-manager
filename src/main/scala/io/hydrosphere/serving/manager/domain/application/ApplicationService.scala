package io.hydrosphere.serving.manager.domain.application

import cats.data._
import cats.effect.Concurrent
import cats.effect.concurrent.Deferred
import cats.effect.implicits._
import cats.implicits._
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.discovery.application.ApplicationDiscoveryHub
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.DomainError.{InvalidRequest, NotFound}
import io.hydrosphere.serving.manager.domain.application.Application._
import io.hydrosphere.serving.manager.domain.application.graph._
import io.hydrosphere.serving.manager.domain.application.requests._
import io.hydrosphere.serving.manager.domain.model_version._
import io.hydrosphere.serving.manager.domain.servable.Servable.OkServable
import io.hydrosphere.serving.manager.domain.servable.ServableService
import io.hydrosphere.serving.manager.grpc.entities.ServingApp
import io.hydrosphere.serving.manager.util.DeferredResult
import io.hydrosphere.serving.manager.{domain, grpc}
import io.hydrosphere.serving.model.api.TensorExampleGenerator
import io.hydrosphere.serving.model.api.json.TensorJsonLens
import org.apache.logging.log4j.scala.Logging
import spray.json.JsObject

import scala.concurrent.ExecutionContext

trait ApplicationService[F[_]] {
  def generateInputs(name: String): F[JsObject]

  def create(appRequest: CreateApplicationRequest): F[DeferredResult[F, GenericApplication]]

  def delete(name: String): F[GenericApplication]

  def update(appRequest: UpdateApplicationRequest): F[DeferredResult[F, GenericApplication]]

  def get(name: String): F[GenericApplication]
}

object ApplicationService extends Logging {

  def apply[F[_]](
    applicationRepository: ApplicationRepository[F],
    versionRepository: ModelVersionRepository[F],
    servableService: ServableService[F],
    discoveryHub: ApplicationDiscoveryHub[F],
    graphComposer: VersionGraphComposer
  )(implicit F: Concurrent[F], ex: ExecutionContext): ApplicationService[F] = new ApplicationService[F] {

    def composeApp(
      name: String,
      namespace: Option[String],
      executionGraph: ExecutionGraphRequest,
      kafkaStreaming: Option[List[ApplicationKafkaStream]]
    ) = {
      for {
        _ <- checkApplicationName(name)
        versions <- executionGraph.stages.traverse { f =>
          for {
            variants <- f.modelVariants.traverse { m =>
              for {
                version <- OptionT(versionRepository.get(m.modelVersionId))
                  .map(Variant(_, m.weight))
                  .getOrElseF(
                    F.raiseError(
                      DomainError
                        .notFound(s"Can't find modelversion $m")
                    )
                  )
                _ <- version.item.status match {
                  case ModelVersionStatus.Released => F.unit
                  case x =>
                    F.raiseError[Unit](
                      DomainError
                        .invalidRequest(s"Can't deploy non-released ModelVersion: ${version.item.fullName} - $x")
                    )
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

    def generateInputs(name: String): F[JsObject] = {
      for {
        app        <- get(name)
        tensorData <- F.delay(TensorExampleGenerator(app.signature).inputs)
        jsonData   <- F.delay(TensorJsonLens.mapToJson(tensorData))
      } yield jsonData
    }

    private def startServices(app: AssemblingApp): F[Either[FailedApp, ReadyApp]] = {
      val finished = for {
        deployedServables <- app.status.versionGraph.traverse { stage =>
          for {
            variants <- stage.modelVariants.traverse { i =>
              for {
                servable <- servableService.deploy(i.item)
              } yield Variant(servable, i.weight)
            }
          } yield ExecutionNode(variants, stage.signature)
        }
        finishedApp = app.copy(status = Application.Ready(deployedServables))
        translated  = Internals.toServingApp(finishedApp)
        _ <- discoveryHub.added(translated)
      } yield finishedApp.asRight[FailedApp]

      finished.handleErrorWith { x =>
        for {
          _ <- F.delay(logger.error(s"ModelVersion deployment exception", x))
        } yield
          app.copy(status = Application.Failed(app.status.versionGraph, Option(x.getMessage)))
            .asLeft[ReadyApp]
      }
    }

    def create(req: CreateApplicationRequest): F[DeferredResult[F, GenericApplication]] = {
      for {
        composedApp <- composeApp(req.name, req.namespace, req.executionGraph, req.kafkaStreaming)
        appId       <- applicationRepository.create(composedApp).map(_.id)
        app = composedApp.copy(id = appId)
        df <- Deferred[F, GenericApplication]
        _ <- (for {
          genericApp <- startServices(app).map {
            case Right(x) => x.generic
            case Left(x)  => x.generic
          }
          _ <- applicationRepository.update(genericApp)
          _ <- df.complete(genericApp)
        } yield ())
          .handleError(x => logger.error("Error while buidling application", x))
          .start
      } yield DeferredResult(app.generic, df)
    }

    def delete(name: String): F[GenericApplication] = {
      for {
        app <- get(name)
        _   <- discoveryHub.removed(app.id)
        _   <- applicationRepository.delete(app.id)
        _ <- app.status match {
          case Application.Ready(graph) =>
            graph.traverse { s =>
              s.variants.traverse { ss =>
                servableService.stop(ss.item.generic.fullName)
              }.void
            }.void
          case _ =>
            F.unit // TODO do we need to delete servables that don't run?
        }
      } yield app
    }

    def update(appRequest: UpdateApplicationRequest): F[DeferredResult[F, GenericApplication]] = {
      for {
        oldApplication <- OptionT(applicationRepository.get(appRequest.id))
          .getOrElseF(
            F.raiseError(
              DomainError
                .notFound(s"Can't find application id ${appRequest.id}")
            )
          )

        _ <- delete(oldApplication.name)

        newApplication <- create(
          CreateApplicationRequest(
            name = appRequest.name,
            namespace = appRequest.namespace,
            executionGraph = appRequest.executionGraph,
            kafkaStreaming = appRequest.kafkaStreaming
          )
        )
      } yield newApplication
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

    override def get(name: String): F[GenericApplication] = {
      OptionT(applicationRepository.get(name))
        .getOrElseF(F.raiseError(NotFound(s"Application with name $name is not found")))
    }
  }

  object Internals extends Logging {

    import io.hydrosphere.serving.manager.grpc.entities.{Servable => GServable, Stage => GStage}

    def toServingApp(app: ReadyApp): ServingApp = {
      val stages = toGStages(app)

      val contract = ModelContract(modelName = app.name, predict = Some(app.signature))

      ServingApp(app.id.toString, app.name, contract.some, stages.toList)
    }

    def modelVersionToGrpcEntity(mv: domain.model_version.ModelVersion): grpc.entities.ModelVersion =
      grpc.entities.ModelVersion(
        id = mv.id,
        version = mv.modelVersion,
        modelType = "",
        status = mv.status.toString,
        selector = mv.hostSelector.map(s => grpc.entities.HostSelector(s.id, s.name)),
        model = Some(grpc.entities.Model(mv.model.id, mv.model.name)),
        contract = Some(ModelContract(mv.modelContract.modelName, mv.modelContract.predict)),
        image = Some(grpc.entities.DockerImage(mv.image.name, mv.image.tag)),
        imageSha = mv.image.sha256.getOrElse(""),
        runtime = Some(grpc.entities.DockerImage(mv.runtime.name, mv.runtime.tag))
      )

    def toGServable(mv: Variant[OkServable]): GServable = {
      GServable(
        mv.item.status.host,
        mv.item.status.port,
        mv.weight,
        Some(modelVersionToGrpcEntity(mv.item.modelVersion))
      )
    }

    def toGStages(app: ReadyApp): NonEmptyList[GStage] = {
      app.status.stages.zipWithIndex.map {
        case (st, i) =>
          val mapped = st.variants.map(toGServable)
          GStage(i.toString, st.signature.some, mapped.toList)
      }
    }
  }

}
