package io.hydrosphere.serving.manager.domain.application

import cats.data._
import cats.effect._
import cats.effect.concurrent.Deferred
import cats.effect.implicits._
import cats.implicits._
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.discovery.DiscoveryHub
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.DomainError.{InvalidRequest, NotFound}
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionRepository}
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableService}
import io.hydrosphere.serving.manager.grpc.entities.ServingApp
import io.hydrosphere.serving.manager.{domain, grpc}
import io.hydrosphere.serving.model.api.TensorExampleGenerator
import io.hydrosphere.serving.model.api.json.TensorJsonLens
import org.apache.logging.log4j.scala.Logging
import spray.json.JsObject

import scala.concurrent.ExecutionContext
import scala.util.Try

trait ApplicationService[F[_]] {
  def generateInputs(name: String): F[JsObject]

  def create(appRequest: CreateApplicationRequest): F[ApplicationBuildResult[F]]

  def delete(name: String): F[Application]

  def update(appRequest: UpdateApplicationRequest): F[ApplicationBuildResult[F]]

  def get(name: String): F[Application]
}

object ApplicationService extends Logging {
  
  def apply[F[_]](
    applicationRepository: ApplicationRepository[F],
    versionRepository: ModelVersionRepository[F],
    servableService: ServableService[F],
    discoveryHub: DiscoveryHub[F]
  )(implicit F: Concurrent[F], ex: ExecutionContext): ApplicationService[F] = new ApplicationService[F] {

    def composeApp(
      name: String,
      namespace: Option[String],
      executionGraph: ExecutionGraphRequest,
      kafkaStreaming: Option[Seq[ApplicationKafkaStream]]
    ): F[Application] = {
      for {
        _ <- checkApplicationName(name)
        graph <- inferGraph(executionGraph)
        signature <- F.fromOption(
          ApplicationValidator.inferPipelineSignature(name, graph),
          DomainError.invalidRequest("Incompatible application stages")
        )
      } yield composeInitApp(name, namespace, graph, signature, kafkaStreaming.getOrElse(List.empty))
    }

    def generateInputs(name: String): F[JsObject] = {
      for {
        app <- get(name)
        tensorData <- F.fromTry(Try(TensorExampleGenerator(app.signature).inputs))
        jsonData <- F.fromTry(Try(TensorJsonLens.mapToJson(tensorData)))
      } yield jsonData
    }

    private def startServices(application: Application, versionsToDeploy: List[ModelVersion]): F[Application] = {
      val finished = for {
        deployedServables <- versionsToDeploy.traverse(mv => servableService.deploy(mv.servableName, mv.id, mv.image))
        finishedApp = application.copy(status = ApplicationStatus.Ready)
        translated <- F.fromEither(Internals.toServingApp(finishedApp, deployedServables))
        _ <- discoveryHub.added(translated)
        _ <- applicationRepository.update(finishedApp)
      } yield finishedApp

      F.handleErrorWith(finished) { x =>
        for {
          _ <- F.delay(logger.error(s"ModelVersion deployment exception", x))
          failedApp = application.copy(status = ApplicationStatus.Failed)
          _ <- applicationRepository.update(failedApp)
        } yield failedApp
      }
    }

    def create(appRequest: CreateApplicationRequest): F[ApplicationBuildResult[F]] = {
      val keys = for {
        stage <- appRequest.executionGraph.stages
        service <- stage.modelVariants
      } yield {
        service.modelVersionId
      }
      val keySet = keys.toSet

      for {
        app <- composeApp(appRequest.name, appRequest.namespace, appRequest.executionGraph, appRequest.kafkaStreaming)
        versions <- versionRepository.get(keySet.toSeq)
        createdApp <- applicationRepository.create(app)
        df <- Deferred[F, Application]
        _ <- startServices(createdApp, versions.toList).flatMap(df.complete).start
      } yield ApplicationBuildResult(createdApp, df)
    }

    def delete(name: String): F[Application] = {
      for {
        app <- get(name)
        _ <- applicationRepository.delete(app.id)
        keysSet = app.executionGraph.stages.flatMap(_.modelVariants.map(_.modelVersion)).toSet
        _ <- removeServiceIfNeeded(keysSet, app.id)
        _ <- discoveryHub.removed(app.id)
      } yield app
    }

    def update(appRequest: UpdateApplicationRequest): F[ApplicationBuildResult[F]] = {
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

    def checkApplicationName(name: String): F[String] = {
      for {
        _ <- F.fromOption(
          ApplicationValidator.name(name),
          InvalidRequest(s"Application name $name contains invalid symbols. It should only contain latin letters, numbers '-' and '_'")
        )
        _ <- OptionT(applicationRepository.get(name))
          .getOrElseF(F.raiseError(InvalidRequest(s"Application with name $name already exists")))
      } yield name
    }

    private def removeServiceIfNeeded(modelVersions: Set[ModelVersion], applicationId: Long): F[Unit] = {
      val keysSet = modelVersions.map(_.id)
      for {
        apps <- applicationRepository.applicationsWithCommonServices(keysSet, applicationId)
        commonServiceKeys = apps.flatMap(_.executionGraph.stages.flatMap(_.modelVariants.map(_.modelVersion.id))).toSet
        servicesToDelete = modelVersions.filter(mv => !commonServiceKeys.contains(mv.id))
        _ <- servicesToDelete.toList.map(_.servableName).traverse(servableService.stop)
      } yield Unit
    }

    private def composeInitApp(name: String, namespace: Option[String], graph: ApplicationExecutionGraph, signature: ModelSignature, kafkaStreaming: Seq[ApplicationKafkaStream], id: Long = 0) = {
      Application(
        id = id,
        name = name,
        namespace = namespace,
        signature = signature,
        executionGraph = graph,
        kafkaStreaming = kafkaStreaming.toList,
        status = ApplicationStatus.Assembling
      )
    }

    private def inferGraph(executionGraphRequest: ExecutionGraphRequest): F[ApplicationExecutionGraph] = {
      for {
        appStages <- executionGraphRequest.stages match {
          case singleStage :: Nil if singleStage.modelVariants.lengthCompare(1) == 0 =>
            inferSimpleApp(singleStage) // don't perform checks
          case stages =>
            inferPipelineApp(stages)
        }
      } yield ApplicationExecutionGraph(appStages)
    }

    private def inferSimpleApp(singleStage: PipelineStageRequest): F[List[PipelineStage]] = {
      val service = singleStage.modelVariants.head
      for {
        version <- OptionT(versionRepository.get(service.modelVersionId))
          .getOrElseF(F.raiseError(InvalidRequest(s"Can't find model version with id ${service.modelVersionId}")))
        signature <- F.fromOption(
          version.modelContract.predict, InvalidRequest(s"Can't find predict signature")
        )
      } yield List(
        PipelineStage(
          modelVariants = List(ModelVariant(version, 100)), // 100 since this is the only service in the app
          signature = signature,
        )
      )
    }

    private def inferPipelineApp(stages: List[PipelineStageRequest]): F[List[PipelineStage]] = {
      stages.traverse { stage =>
        for {
          services <- inferServices(stage.modelVariants.toList)
          stageSig <- F.fromEither(ApplicationValidator
            .inferStageSignature(services)
            .left
            .map(x => DomainError.invalidRequest(x.message))
          )
        } yield {
          PipelineStage(
            modelVariants = services,
            signature = stageSig,
          )
        }
      }
    }

    private def inferServices(services: List[ModelVariantRequest]): F[List[ModelVariant]] = {
      services.traverse { service =>
        for {
          version <- OptionT(versionRepository.get(service.modelVersionId))
            .getOrElseF(F.raiseError(DomainError.invalidRequest(s"Can't find model version with id ${service.modelVersionId}")))
        } yield ModelVariant(version, service.weight)
      }
    }

    override def get(name: String): F[Application] = {
      OptionT(applicationRepository.get(name))
        .getOrElseF(F.raiseError(NotFound(s"Application with name $name is not found")))
    }
  }

  object Internals extends Logging {

    import io.hydrosphere.serving.manager.grpc.entities.{Servable => GServable, Stage => GStage}

    def toServingApp(
      app: Application,
      servables: List[Servable]
    ): Either[Throwable, ServingApp] = {
      toGStages(app, servables).map(stages => {
        val contract = ModelContract(
          modelName = app.name,
          predict = Some(app.signature)
        )
        
        ServingApp(
          app.id.toString,
          app.name,
          contract.some,
          stages
        )
      })
    }

    def modelVersionToGrpcEntity(mv: domain.model_version.ModelVersion): grpc.entities.ModelVersion = grpc.entities.ModelVersion(
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

    def toGServable(mv: ModelVariant, servables: Map[Long, Servable]): Either[Throwable, GServable] = {
      servables.get(mv.modelVersion.id) match {
        case Some(Servable(_, _, Servable.Status.Running(host, port))) => GServable(host, port, mv.weight, Some(modelVersionToGrpcEntity(mv.modelVersion))).asRight
        case Some(s) => new Exception(s"Invalid servable state for ${mv.modelVersion.model.name}:${mv.modelVersion.id} - $s").asLeft // TODO what about starting servables?
        case None => new Exception(s"Could not find servable for  ${mv.modelVersion.model.name}:${mv.modelVersion.id}").asLeft
      }
    }
  
    def toGStages(app: Application, servables: List[Servable]): Either[Throwable, List[GStage]] = {
      val asMap = servables.map(s => s.modelVersionId -> s).toMap
      app.executionGraph.stages.toList.zipWithIndex.traverse({
        case (st, i) =>
          val mapped = st.modelVariants.toList.traverse(mv => toGServable(mv, asMap))
          mapped.map(servables => GStage(i.toString, st.signature.some, servables))
      })
    }
  }
}