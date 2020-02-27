package io.hydrosphere.serving.manager.domain.model_build

import java.time.Instant

import cats.effect.Concurrent
import cats.effect.concurrent.Deferred
import cats.effect.implicits._
import cats.implicits._
import com.spotify.docker.client.ProgressHandler
import io.hydrosphere.serving.manager.domain.image.{ImageBuilder, ImageRepository}
import io.hydrosphere.serving.manager.domain.model.{Model, ModelVersionMetadata}
import io.hydrosphere.serving.manager.domain.model_version._
import io.hydrosphere.serving.manager.infrastructure.storage.{ModelFileStructure, StorageOps}
import io.hydrosphere.serving.manager.util.DeferredResult
import org.apache.logging.log4j.scala.Logging

trait ModelVersionBuilder[F[_]]{
  def build(model: Model, metadata: ModelVersionMetadata, modelFileStructure: ModelFileStructure): F[DeferredResult[F, ModelVersion.Internal]]
}

object ModelVersionBuilder {
  def apply[F[_] : Concurrent]()(
  implicit
    imageBuilder: ImageBuilder[F],
    modelVersionRepository: ModelVersionRepository[F],
    imageRepository: ImageRepository[F],
    modelVersionService: ModelVersionService[F],
    storageOps: StorageOps[F],
    modelDiscoveryHub: ModelVersionEvents.Publisher[F],
    buildLoggingService: BuildLoggingService[F]
  ): ModelVersionBuilder[F] = new ModelVersionBuilder[F] with Logging {
    override def build(model: Model, metadata: ModelVersionMetadata, modelFileStructure: ModelFileStructure): F[DeferredResult[F, ModelVersion.Internal]] = {
      for {
        init <- initialVersion(model, metadata)
        handler <- buildLoggingService.makeLogger(init)
        _ <- modelDiscoveryHub.update(init)
        deferred <- Deferred[F, ModelVersion.Internal]
        _ <- handleBuild(init, modelFileStructure, handler).flatMap(deferred.complete).start
      } yield DeferredResult(init, deferred)
    }

    def initialVersion(model: Model, metadata: ModelVersionMetadata) = {
      for {
        version <- modelVersionService.getNextModelVersion(model.id)
        image = imageRepository.getImage(metadata.modelName, version.toString)
        mv = ModelVersion.Internal(
          id = 0,
          image = image,
          created = Instant.now(),
          finished = None,
          modelVersion = version,
          modelContract = metadata.contract,
          runtime = metadata.runtime,
          model = model,
          hostSelector = metadata.hostSelector,
          status = ModelVersionStatus.Assembling,
          installCommand = metadata.installCommand,
          metadata = metadata.metadata,
        )
        modelVersion <- modelVersionRepository.create(mv)
      } yield mv.copy(id = modelVersion.id)
    }

    def handleBuild(mv: ModelVersion.Internal, modelFileStructure: ModelFileStructure, handler: ProgressHandler) = {
      val innerCompleted = for {
        buildPath <- prepare(mv, modelFileStructure)
        imageSha <- imageBuilder.build(buildPath.root, mv.image, handler)
        newDockerImage = mv.image.copy(sha256 = Some(imageSha))
        finishedVersion = mv.copy(image = newDockerImage, finished = Instant.now().some, status = ModelVersionStatus.Released)
        _ <- imageRepository.push(finishedVersion.image, handler)
        _ <- buildLoggingService.finishLogging(mv.id)
        _ <- modelVersionRepository.update(finishedVersion)
        _ <- modelDiscoveryHub.update(finishedVersion)
      } yield finishedVersion

      innerCompleted.handleErrorWith { err =>
        for {
          _ <- Concurrent[F].delay(logger.error("Model version build failed", err))
          failed = mv.copy(status = ModelVersionStatus.Failed, finished = Instant.now().some)
          _ <- buildLoggingService.finishLogging(mv.id).attempt
          _ <- modelDiscoveryHub.update(failed).attempt
          _ <- modelVersionRepository.update(failed).attempt
        } yield failed
      }
    }

    def prepare(modelVersion: ModelVersion.Internal, modelFileStructure: ModelFileStructure): F[ModelFileStructure] = {
      for {
        _ <- storageOps.writeBytes(modelFileStructure.dockerfile, BuildScript.generate(modelVersion).getBytes)
        _ <- storageOps.writeBytes(modelFileStructure.contractPath, modelVersion.modelContract.toByteArray)
      } yield modelFileStructure
    }
  }
}