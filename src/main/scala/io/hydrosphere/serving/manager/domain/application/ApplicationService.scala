package io.hydrosphere.serving.manager.domain.application

import cats.data._
import cats.effect.Concurrent
import cats.implicits._
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.DomainError.NotFound
import io.hydrosphere.serving.manager.domain.application.requests._
import io.hydrosphere.serving.manager.domain.model_version._
import io.hydrosphere.serving.manager.domain.servable.{ServableGC, ServableService}
import io.hydrosphere.serving.manager.util.DeferredResult
import io.hydrosphere.serving.model.api.TensorExampleGenerator
import io.hydrosphere.serving.model.api.json.TensorJsonLens
import org.apache.logging.log4j.scala.Logging
import spray.json.JsObject

trait ApplicationService[F[_]] {
  def all(): F[List[Application]]

  def generateInputs(name: String): F[JsObject]

  def create(appRequest: CreateApplicationRequest): F[DeferredResult[F, Application]]

  def delete(name: String): F[Application]

  def update(appRequest: UpdateApplicationRequest): F[DeferredResult[F, Application]]

  def get(name: String): F[Application]
}

object ApplicationService extends Logging {

  def apply[F[_]]()(
    implicit
    F: Concurrent[F],
    applicationRepository: ApplicationRepository[F],
    versionRepository: ModelVersionRepository[F],
    servableService: ServableService[F],
    applicationDeployer: ApplicationDeployer[F],
    servableGC: ServableGC[F]
  ): ApplicationService[F] = new ApplicationService[F] {

    def generateInputs(name: String): F[JsObject] = {
      for {
        app <- get(name)
        tensorData <- F.delay(TensorExampleGenerator(app.signature).inputs)
        jsonData <- F.delay(TensorJsonLens.mapToJson(tensorData))
      } yield jsonData
    }

    def create(req: CreateApplicationRequest): F[DeferredResult[F, Application]] = {
      applicationDeployer.deploy(req.name, req.executionGraph, req.kafkaStreaming.getOrElse(List.empty), req.metadata.getOrElse(Map.empty))
    }

    def delete(name: String): F[Application] = {
      for {
        app <- get(name)
        _ <- applicationRepository.delete(app.id)
        _ <- app.graph.stages.traverse { stage =>
          stage.variants.traverse { variant =>
            servableGC.mark(variant.modelVersion).void >>
              variant.servable.fold(F.unit)(s => servableService.stop(s.fullName).void)
          }
        }
      } yield app
    }

    def update(appRequest: UpdateApplicationRequest): F[DeferredResult[F, Application]] = {
      for {
        oldApplication <- OptionT(applicationRepository.get(appRequest.id))
          .getOrElseF(F.raiseError(DomainError.notFound(s"Can't find application id ${appRequest.id}")))

        _ <- delete(oldApplication.name)

        newApplication <- create(
          CreateApplicationRequest(
            name = appRequest.name,
            namespace = appRequest.namespace,
            executionGraph = appRequest.executionGraph,
            kafkaStreaming = appRequest.kafkaStreaming,
            metadata = None
          )
        )
      } yield newApplication
    }

    override def get(name: String): F[Application] = {
      OptionT(applicationRepository.get(name))
        .getOrElseF(F.raiseError(NotFound(s"Application with name $name is not found")))
    }

    override def all(): F[List[Application]] = applicationRepository.all()
  }
}