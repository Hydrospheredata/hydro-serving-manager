package io.hydrosphere.serving.manager.domain.model_version

import cats.data.OptionT
import cats.implicits._
import cats.{MonadError, Traverse}
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.application.ApplicationRepository
import io.hydrosphere.serving.manager.domain.model.ModelValidator
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.ExecutionContext

trait ModelVersionService[F[_]] {
  def get(name: String, version: Long): F[ModelVersion]

  def deleteVersions(mvs: Seq[ModelVersion]): F[Seq[ModelVersion]]

  def getNextModelVersion(modelId: Long): F[Long]

  def list: F[Seq[ModelVersionView]]

  def delete(versionId: Long): F[Option[ModelVersion]]
}

object ModelVersionService {
  def apply[F[_]](
    modelVersionRepository: ModelVersionRepository[F],
    applicationRepo: ApplicationRepository[F]
  )(
    implicit F: MonadError[F, Throwable],
    executionContext: ExecutionContext
  ): ModelVersionService[F] = new ModelVersionService[F] with Logging {

    def deleteVersions(mvs: Seq[ModelVersion]): F[Seq[ModelVersion]] = {
      Traverse[List].traverse(mvs.toList) { version =>
        delete(version.id)
      }.map(_.flatten)
    }

    def list: F[Seq[ModelVersionView]] = {
      for {
        allVersions <- modelVersionRepository.all()
        f <- allVersions.map(_.id).toList.traverse { x =>
          applicationRepo.findVersionsUsage(x).map(x -> _)
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
      } yield version
      f.value
    }

    def getNextModelVersion(modelId: Long): F[Long] = {
      for {
        versions <- modelVersionRepository.lastModelVersionByModel(modelId, 1)
      } yield versions.headOption.fold(1L)(_.modelVersion + 1)
    }

    override def get(name: String, version: Long): F[ModelVersion] = {
      for {
        _ <- F.fromOption(ModelValidator.name(name), DomainError.invalidRequest("Name contains invalid characters."))

        mv <- OptionT(modelVersionRepository.get(name, version))
            .getOrElseF(F.raiseError(DomainError.notFound(s"Can't find a ModelVersion $name:$version")))
      } yield mv
    }
  }
}