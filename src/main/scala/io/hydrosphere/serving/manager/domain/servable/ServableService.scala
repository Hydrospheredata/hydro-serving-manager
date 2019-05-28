package io.hydrosphere.serving.manager.domain.servable

import cats.data.OptionT
import cats.effect._
import cats.implicits._
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.clouddriver._
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionRepository}
import io.hydrosphere.serving.manager.domain.servable.Servable.{GenericServable, OkServable}
import io.hydrosphere.serving.manager.util.random.NameGenerator
import org.apache.logging.log4j.scala.Logging

import scala.util.control.NonFatal

trait ServableService[F[_]] {
  def findAndDeploy(name: String, version: Long): F[OkServable]

  def stop(name: String): F[GenericServable]

  def deploy(modelVersion: ModelVersion): F[OkServable]
}

object ServableService extends Logging {
  def apply[F[_]](
    cloudDriver: CloudDriver[F],
    servableRepository: ServableRepository[F],
    versionRepository: ModelVersionRepository[F],
    nameGenerator: NameGenerator[F],
    monitor: ServableMonitor[F]
  )(implicit F: Sync[F], timer: Timer[F]): ServableService[F] = new ServableService[F] {

    override def deploy(modelVersion: ModelVersion): F[OkServable] = {
      for {
        randomSuffix <- nameGenerator.getName()
        fullName = Servable.fullName(modelVersion.model.name, modelVersion.modelVersion, randomSuffix)
        servable <- awaitServable(fullName, randomSuffix, modelVersion)
          .onError {
            case NonFatal(ex) => cloudDriver.remove(fullName) >> F.delay(logger.error(ex))
          }
      } yield servable
    }

    def awaitServable(fullName: String, suffix: String, modelVersion: ModelVersion): F[OkServable] = {
      for {
        _ <- cloudDriver.run(fullName, modelVersion.id, modelVersion.image)
        status <- monitor.monitor(fullName)
        s <- status match {
          case x: Servable.Serving => F.pure(Servable(modelVersion, suffix, x))
          case x => F.raiseError[OkServable](DomainError.internalError(s"Servable $fullName is in invalid state: $x"))
        }
        _ <- servableRepository.upsert(s)
      } yield s
    }

    override def stop(name: String): F[Servable.GenericServable] = {
      for {
        servable <- OptionT(servableRepository.get(name))
          .getOrElseF(F.raiseError(DomainError.notFound(s"Can't stop Servable $name because it doesn't exist")))
        _ <- cloudDriver.remove(name)
        _ <- servableRepository.delete(name)
      } yield servable
    }

    override def findAndDeploy(name: String, version: Long): F[OkServable] = {
      for {
        version <- OptionT(versionRepository.get(name, version))
          .getOrElseF(F.raiseError(DomainError.notFound(s"Model $name:$version doesn't exist")))
        servable <- deploy(version)
      } yield servable
    }
  }
}
