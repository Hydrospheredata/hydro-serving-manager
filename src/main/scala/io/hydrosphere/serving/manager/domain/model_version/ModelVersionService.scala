package io.hydrosphere.serving.manager.domain.model_version

import cats.MonadError
import cats.data.OptionT
import cats.implicits._
import io.hydrosphere.serving.manager.discovery.ModelPublisher
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.application.ApplicationRepository
import io.hydrosphere.serving.manager.domain.model.ModelValidator
import org.apache.logging.log4j.scala.Logging

trait ModelVersionService[F[_]] {
  def all(): F[List[ModelVersion]]

  def get(id: Long): F[ModelVersion]

  def get(name: String, version: Long): F[ModelVersion]

  def getNextModelVersion(modelId: Long): F[Long]

  def list: F[List[ModelVersionView]]

  def listForModel(modelId: Long): F[List[ModelVersion]]

  def delete(versionId: Long): F[Option[ModelVersion]]
}

object ModelVersionService {
  def apply[F[_]]()(
    implicit F: MonadError[F, Throwable],
    modelVersionRepository: ModelVersionRepository[F],
    applicationRepo: ApplicationRepository[F],
    modelPublisher: ModelPublisher[F]
  ): ModelVersionService[F] = new ModelVersionService[F] with Logging {

    def list: F[List[ModelVersionView]] = {
      for {
        allVersions <- modelVersionRepository.all()
        f <- allVersions.map(_.id).traverse { x =>
          applicationRepo.findVersionUsage(x).map(x -> _)
        }
        usageMap = f.toMap
      } yield {
        allVersions.map { v =>
          ModelVersionView.fromVersion(v, usageMap.getOrElse(v.id, Seq.empty))
        }
      }
    }

    def delete(versionId: Long): F[Option[ModelVersion]] = {
      val f = for {
        version <- OptionT(modelVersionRepository.get(versionId))
        _ <- OptionT.liftF(modelVersionRepository.delete(versionId))
        _ <- OptionT.liftF(modelPublisher.remove(versionId))
      } yield version
      f.value
    }

    def getNextModelVersion(modelId: Long): F[Long] = {
      for {
        versions <- modelVersionRepository.lastModelVersionByModel(modelId)
      } yield versions.fold(1L)(_.modelVersion + 1)
    }

    override def get(name: String, version: Long): F[ModelVersion] = {
      for {
        _ <- F.fromOption(ModelValidator.name(name), DomainError.invalidRequest("Name contains invalid characters."))
        mv <- OptionT(modelVersionRepository.get(name, version))
          .getOrElseF(F.raiseError(DomainError.notFound(s"Can't find a ModelVersion $name:$version")))
      } yield mv
    }

    override def all(): F[List[ModelVersion]] = modelVersionRepository.all()

    override def get(id: Long): F[ModelVersion] = {
      for {
        mv <- OptionT(modelVersionRepository.get(id))
          .getOrElseF(F.raiseError(DomainError.notFound(s"Can't find a ModelVersion $id")))
      } yield mv
    }

    override def listForModel(modelId: Long): F[List[ModelVersion]] = {
      modelVersionRepository.listForModel(modelId)
    }
  }
}