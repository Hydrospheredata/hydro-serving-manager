package io.hydrosphere.serving.manager.domain.application

import cats.data._
import cats.effect.Concurrent
import cats.implicits._
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.discovery.application.ApplicationDiscoveryHub
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.DomainError.NotFound
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
    applicationDeployer: ApplicationDeployer[F]
  )(implicit F: Concurrent[F], ex: ExecutionContext): ApplicationService[F] = new ApplicationService[F] {

    def generateInputs(name: String): F[JsObject] = {
      for {
        app <- get(name)
        tensorData <- F.delay(TensorExampleGenerator(app.signature).inputs)
        jsonData <- F.delay(TensorJsonLens.mapToJson(tensorData))
      } yield jsonData
    }


    def create(req: CreateApplicationRequest): F[DeferredResult[F, GenericApplication]] = {
      applicationDeployer.deploy(req.name, req.executionGraph, req.kafkaStreaming.getOrElse(List.empty))
    }

    def delete(name: String): F[GenericApplication] = {
      for {
        app <- get(name)
        _ <- discoveryHub.removed(app.id)
        _ <- applicationRepository.delete(app.id)
        _ <- app.status match {
          case Application.Ready(graph) =>
            graph.traverse { s =>
              s.variants.traverse { ss =>
                servableService.stop(ss.item.fullName)
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
          .getOrElseF(F.raiseError(DomainError.notFound(s"Can't find application id ${appRequest.id}")))

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

    override def get(name: String): F[GenericApplication] = {
      OptionT(applicationRepository.get(name))
        .getOrElseF(F.raiseError(NotFound(s"Application with name $name is not found")))
    }
  }

  object Internals extends Logging {

    import io.hydrosphere.serving.manager.grpc.entities.{Servable => GServable, Stage => GStage}

    def toServingApp(app: ReadyApp): ServingApp = {
      val stages = toGStages(app)

      val contract = ModelContract(modelName = app.name, predict = app.signature.some)

      ServingApp(app.id.toString, app.name, contract.some, stages.toList)
    }

    def modelVersionToGrpcEntity(mv: domain.model_version.ModelVersion): grpc.entities.ModelVersion =
      grpc.entities.ModelVersion(
        id = mv.id,
        version = mv.modelVersion,
        modelType = "",
        status = mv.status.toString,
        selector = mv.hostSelector.map(s => grpc.entities.HostSelector(s.id, s.name)),
        model = grpc.entities.Model(mv.model.id, mv.model.name).some,
        contract = ModelContract(mv.modelContract.modelName, mv.modelContract.predict).some,
        image = grpc.entities.DockerImage(mv.image.name, mv.image.tag).some,
        imageSha = mv.image.sha256.getOrElse(""),
        runtime = grpc.entities.DockerImage(mv.runtime.name, mv.runtime.tag).some
      )

    def toGServable(mv: Variant[OkServable]): GServable = {
      GServable(
        host = mv.item.status.host,
        port = mv.item.status.port,
        weight = mv.weight,
        modelVersion = modelVersionToGrpcEntity(mv.item.modelVersion).some,
        name = mv.item.fullName
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
