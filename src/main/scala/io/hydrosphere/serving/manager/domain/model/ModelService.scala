package io.hydrosphere.serving.manager.domain.model

import java.nio.file.Path

import cats.data.OptionT
import cats.implicits._
import cats.{Monad, MonadError}
import io.hydrosphere.serving.manager.api.http.controller.model.ModelUploadMetadata
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.DomainError.{InvalidRequest, NotFound}
import io.hydrosphere.serving.manager.domain.application.Application.GenericApplication
import io.hydrosphere.serving.manager.domain.application.ApplicationRepository
import io.hydrosphere.serving.manager.domain.host_selector.{HostSelector, HostSelectorRepository}
import io.hydrosphere.serving.manager.domain.model_build.ModelVersionBuilder
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionRepository, ModelVersionService}
import io.hydrosphere.serving.manager.infrastructure.storage.ModelUnpacker
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.ModelFetcher
import io.hydrosphere.serving.manager.util.DeferredResult
import org.apache.logging.log4j.scala.Logging

trait ModelService[F[_]] {
  def get(modelId: Long): F[Model]

  def deleteModel(modelId: Long): F[Model]

  def uploadModel(filePath: Path, meta: ModelUploadMetadata): F[DeferredResult[F, ModelVersion]]

  def checkIfUnique(targetModel: Model, newModelInfo: Model): F[Model]

  def checkIfNoApps(versions: Seq[ModelVersion]): F[Unit]
}

object ModelService {
  def apply[F[_]]()(
    implicit
    F: MonadError[F, Throwable],
    modelRepository: ModelRepository[F],
    modelVersionService: ModelVersionService[F],
    modelVersionRepository: ModelVersionRepository[F],
    storageService: ModelUnpacker[F],
    appRepo: ApplicationRepository[F],
    hostSelectorRepository: HostSelectorRepository[F],
    fetcher: ModelFetcher[F],
    modelVersionBuilder: ModelVersionBuilder[F]
  ): ModelService[F] = new ModelService[F] with Logging {

    def deleteModel(modelId: Long): F[Model] = {
      for {
        model <- get(modelId)
        versions <- modelVersionRepository.listForModel(model.id)
        _ <- checkIfNoApps(versions)
        _ <- modelVersionService.deleteVersions(versions)
        _ <- modelRepository.delete(model.id)
      } yield model
    }

    def uploadModel(filePath: Path, meta: ModelUploadMetadata): F[DeferredResult[F, ModelVersion]] = {
      val maybeHostSelector = meta.hostSelectorName match {
        case Some(value) =>
          OptionT(hostSelectorRepository.get(value))
            .map(_.some)
            .getOrElseF(F.raiseError(DomainError.invalidRequest(s"Can't find host selector named $value")))
        case None => F.pure(none[HostSelector])
      }

      for {
        _ <- F.fromOption(ModelValidator.name(meta.name), DomainError.invalidRequest("Model name contains invalid characters"))
        hs <- maybeHostSelector
        modelPath <- storageService.unpack(filePath)
        fetchResult <- fetcher.fetch(modelPath.filesPath)
        versionMetadata = ModelVersionMetadata.combineMetadata(fetchResult, meta, hs)
        _ <- F.fromEither(ModelVersionMetadata.validateContract(versionMetadata))
        parentModel <- createIfNecessary(versionMetadata.modelName)
        b <- modelVersionBuilder.build(parentModel, versionMetadata, modelPath)
      } yield b
    }

    def createIfNecessary(modelName: String): F[Model] = {
      modelRepository.get(modelName).flatMap {
        case Some(x) => Monad[F].pure(x)
        case None => modelRepository.create(Model(0, modelName))
      }
    }

    def checkIfUnique(targetModel: Model, newModelInfo: Model): F[Model] = {
      modelRepository.get(newModelInfo.name).flatMap {
        case Some(model) if model.id == targetModel.id => // it's the same model - ok
          F.pure(targetModel)

        case Some(model) => // it's other model - not ok
          val errMsg = InvalidRequest(s"There is already a model with same name: ${model.name}(${model.id}) -> ${newModelInfo.name}(${newModelInfo.id})")
          logger.error(errMsg)
          F.raiseError(errMsg)

        case None => // name is unique - ok
          F.pure(targetModel)
      }
    }

    def checkIfNoApps(versions: Seq[ModelVersion]): F[Unit] = {

      def _checkApps(usedApps: Seq[Seq[GenericApplication]]): Either[DomainError, Unit] = {
        val allApps = usedApps.flatten.map(_.name)
        if (allApps.isEmpty) {
          Right(())
        } else {
          val appNames = allApps.mkString(", ")
          Left(DomainError.invalidRequest(s"Can't delete the model. It's used in [$appNames]."))
        }
      }

      for {
        usedApps <- versions.map(_.id).toList.traverse(appRepo.findVersionUsage)
        _ <- F.fromEither(_checkApps(usedApps))
      } yield ()
    }

    override def get(modelId: Long): F[Model] = {
      OptionT(modelRepository.get(modelId))
        .getOrElseF(F.raiseError(NotFound(s"Can't find a model with id $modelId"))
        )
    }
  }
}
