package io.hydrosphere.serving.manager.domain.model

import java.nio.file.Path
import cats.data.OptionT
import cats.effect.Clock
import cats.implicits._
import cats.{Monad, MonadError}
import io.hydrosphere.serving.manager.api.http.controller.model._
import io.hydrosphere.serving.manager.domain.DomainError._
import io.hydrosphere.serving.manager.domain.application.{Application, ApplicationRepository}
import io.hydrosphere.serving.manager.domain.model_build.ModelVersionBuilder
import io.hydrosphere.serving.manager.domain.model_version._
import io.hydrosphere.serving.manager.domain.monitoring.MonitoringConfiguration
import io.hydrosphere.serving.manager.domain.servable.ServableRepository
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.infrastructure.storage.ModelUnpacker
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.ModelFetcher
import io.hydrosphere.serving.manager.util.DeferredResult
import io.hydrosphere.serving.manager.util.InstantClockSyntax._
import org.apache.logging.log4j.scala.Logging

trait ModelService[F[_]] {
  def get(modelId: Long): F[Model]

  def deleteModel(modelId: Long): F[Model]

  def uploadModel(
      filePath: Path,
      meta: ModelUploadMetadata
  ): F[DeferredResult[F, ModelVersion.Internal]]

  def registerModel(modelReq: RegisterModelRequest): F[ModelVersion.External]

  def checkIfUnique(targetModel: Model, newModelInfo: Model): F[Model]

  def checkIfNoApps(versions: Seq[ModelVersion]): F[Unit]
}

object ModelService {
  def apply[F[_]]()(implicit
      F: MonadError[F, Throwable],
      clock: Clock[F],
      modelRepository: ModelRepository[F],
      modelVersionRepository: ModelVersionRepository[F],
      modelVersionService: ModelVersionService[F],
      storageService: ModelUnpacker[F],
      appRepo: ApplicationRepository[F],
      servableRepo: ServableRepository[F],
      fetcher: ModelFetcher[F],
      modelVersionBuilder: ModelVersionBuilder[F]
  ): ModelService[F] =
    new ModelService[F] with Logging {

      def deleteModel(modelId: Long): F[Model] =
        for {
          model    <- get(modelId)
          versions <- modelVersionService.listForModel(model.id)
          _        <- checkIfNoApps(versions)
          _        <- checkIfNoServables(versions)
          _        <- versions.traverse(x => modelVersionService.delete(x.id))
          _        <- modelRepository.delete(model.id)
        } yield model

      def uploadModel(
          filePath: Path,
          meta: ModelUploadMetadata
      ): F[DeferredResult[F, ModelVersion.Internal]] =
        for {
          _ <- F.fromOption(
            ModelValidator.name(meta.name),
            DomainError.invalidRequest("Model name contains invalid characters")
          )
          modelPath   <- storageService.unpack(filePath)
          fetchResult <- fetcher.fetch(modelPath.filesPath)
          versionMetadata <- F.fromOption(
            ModelVersionMetadata
              .combineMetadata(fetchResult, meta),
            DomainError.invalidRequest(s"No signature provided for a model  ${meta.name}")
          )
          _ <- F.fromValidated(
            Signature
              .validate(versionMetadata.signature)
              .leftMap(x => InvalidRequest(x.toList.mkString))
          )
          parentModel <- createIfNecessary(versionMetadata.modelName)
          b           <- modelVersionBuilder.build(parentModel, versionMetadata, modelPath)
        } yield b

      def createIfNecessary(modelName: String): F[Model] =
        modelRepository.get(modelName).flatMap {
          case Some(x) => Monad[F].pure(x)
          case None    => modelRepository.create(Model(0, modelName))
        }

      def checkIfUnique(targetModel: Model, newModelInfo: Model): F[Model] =
        modelRepository.get(newModelInfo.name).flatMap {
          case Some(model) if model.id == targetModel.id => // it's the same model - ok
            F.pure(targetModel)

          case Some(model) => // it's other model - not ok
            val errMsg = InvalidRequest(
              s"There is already a model with same name: ${model.name}(${model.id}) -> ${newModelInfo.name}(${newModelInfo.id})"
            )
            logger.error(errMsg)
            F.raiseError(errMsg)

          case None => // name is unique - ok
            F.pure(targetModel)
        }

      def checkIfNoApps(versions: Seq[ModelVersion]): F[Unit] = {

        def _checkApps(
            usedApps: Seq[Seq[Application]]
        ): Either[DomainError, Unit] = {
          val allApps = usedApps.flatten.map(_.name)
          if (allApps.isEmpty)
            Right(())
          else {
            val appNames = allApps.mkString(", ")
            Left(
              DomainError.invalidRequest(
                s"Can't delete the model. It's used in [$appNames]."
              )
            )
          }
        }

        for {
          usedApps <- versions.map(_.id).toList.traverse(appRepo.findVersionUsage)
          _        <- F.fromEither(_checkApps(usedApps))
        } yield ()
      }

      def checkIfNoServables(versions: List[ModelVersion]) =
        versions.traverse { version =>
          for {
            servables <- servableRepo.findForModelVersion(version.id)
            _ <- servables match {
              case Nil => F.unit
              case x =>
                DomainError
                  .invalidRequest(
                    s"Can't delete the model. ${version.fullName} is used in ${x
                      .map(_.name)}"
                  )
                  .raiseError[F, Unit]
            }
          } yield ()
        }.void

      override def get(modelId: Long): F[Model] =
        OptionT(modelRepository.get(modelId))
          .getOrElseF(
            F.raiseError(NotFound(s"Can't find a model with id $modelId"))
          )

      override def registerModel(
          modelReq: RegisterModelRequest
      ): F[ModelVersion.External] =
        for {
          _ <- F.fromOption(
            ModelValidator.name(modelReq.name),
            DomainError.invalidRequest("Model name contains invalid characters")
          )
          _ <- F.fromValidated(
            Signature
              .validate(modelReq.signature)
              .leftMap(x => InvalidRequest(x.toList.mkString))
          )
          parentModel <- createIfNecessary(modelReq.name)
          version     <- modelVersionService.getNextModelVersion(parentModel.id)
          timestamp   <- clock.instant()
          mv = ModelVersion.External(
            id = 0,
            created = timestamp,
            modelVersion = version,
            modelSignature = modelReq.signature,
            model = parentModel,
            metadata = modelReq.metadata.getOrElse(Map.empty),
            monitoringConfiguration =
              modelReq.monitoringConfiguration.getOrElse(MonitoringConfiguration())
          )
          ver <- modelVersionRepository.create(mv)
        } yield mv.copy(id = ver.id)
    }
}
