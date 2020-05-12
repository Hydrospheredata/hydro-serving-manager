package io.hydrosphere.serving.manager.domain.application

import cats.data._
import cats.effect.Concurrent
import cats.implicits._
import io.circe.Json
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.DomainError.NotFound
import io.hydrosphere.serving.manager.domain.application.requests._
import io.hydrosphere.serving.manager.domain.model_version._
import io.hydrosphere.serving.manager.domain.servable.{ServableGC, ServableService}
import io.hydrosphere.serving.manager.domain.tensor.TensorExampleGenerator
import io.hydrosphere.serving.manager.domain.tensor.json.TensorJsonLens
import io.hydrosphere.serving.manager.util.{DeferredResult, UnsafeLogging}

trait ApplicationService[F[_]] {
  def all(): F[List[Application]]

  def create(appRequest: CreateApplicationRequest): F[DeferredResult[F, Application]]

  def delete(name: String): F[Application]

  def update(appRequest: UpdateApplicationRequest): F[DeferredResult[F, Application]]

  def get(name: String): F[Application]

  def generateInputs(name: String): F[Json]
}

object ApplicationService extends UnsafeLogging {

  def apply[F[_]](
      applicationRepository: ApplicationRepository[F],
      versionRepository: ModelVersionRepository[F],
      servableService: ServableService[F],
      discoveryHub: ApplicationEvents.Publisher[F],
      applicationDeployer: ApplicationDeployer[F],
      servableGC: ServableGC[F]
  )(implicit
      F: Concurrent[F]
  ): ApplicationService[F] =
    new ApplicationService[F] {
      def generateInputs(name: String): F[Json] =
        for {
          app        <- get(name)
          tensorData <- F.delay(TensorExampleGenerator(app.executionGraph.signature).inputs)
          jsonData   <- F.delay(TensorJsonLens.mapToJson(tensorData))
        } yield jsonData

      def create(req: CreateApplicationRequest): F[DeferredResult[F, Application]] =
        applicationDeployer.deploy(
          req.name,
          req.executionGraph,
          req.kafkaStreaming.getOrElse(List.empty)
        )

      def delete(name: String): F[Application] =
        for {
          app <- get(name)
          _   <- discoveryHub.remove(app.name)
          _   <- applicationRepository.delete(app.id)
          servableNames =
            app.executionGraph.nodes.toList
              .flatMap(_.variants.toList.flatMap(_.servable.map(_.fullName)))
          _ <- servableNames.traverse(servableService.stop).attempt
          modelVersions =
            app.executionGraph.nodes.toList.flatMap(_.variants.toList.map(_.modelVersion))
          _ <- modelVersions.traverse(servableGC.mark).attempt
        } yield app

      def update(appRequest: UpdateApplicationRequest): F[DeferredResult[F, Application]] =
        for {
          oldApplication <- OptionT(applicationRepository.get(appRequest.id))
            .getOrElseF(
              F.raiseError(DomainError.notFound(s"Can't find application id ${appRequest.id}"))
            )

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

      override def get(name: String): F[Application] =
        OptionT(applicationRepository.get(name))
          .getOrElseF(F.raiseError(NotFound(s"Application with name $name is not found")))

      override def all(): F[List[Application]] = applicationRepository.all()
    }
}
