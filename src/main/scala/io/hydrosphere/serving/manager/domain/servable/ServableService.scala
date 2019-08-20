package io.hydrosphere.serving.manager.domain.servable

import cats.data.OptionT
import cats.effect._
import cats.effect.concurrent.Deferred
import cats.effect.implicits._
import cats.implicits._
import io.hydrosphere.serving.manager.discovery.ServablePublisher
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.application.ApplicationRepository
import io.hydrosphere.serving.manager.domain.clouddriver._
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionRepository}
import io.hydrosphere.serving.manager.domain.servable.Servable.GenericServable
import io.hydrosphere.serving.manager.util.{DeferredResult, UUIDGenerator}
import io.hydrosphere.serving.manager.util.random.NameGenerator
import org.apache.logging.log4j.scala.Logging

import scala.util.control.NonFatal

trait ServableService[F[_]] {
  def all(): F[List[GenericServable]]

  def findAndDeploy(name: String, version: Long): F[DeferredResult[F, GenericServable]]

  def findAndDeploy(modelId: Long): F[DeferredResult[F, GenericServable]]

  def stop(name: String): F[GenericServable]

  def deploy(modelVersion: ModelVersion): F[DeferredResult[F, GenericServable]]
}

object ServableService extends Logging {
  def apply[F[_]]()(
    implicit F: Concurrent[F],
    timer: Timer[F],
    nameGenerator: NameGenerator[F],
    idGenerator: UUIDGenerator[F],
    cloudDriver: CloudDriver[F],
    servableRepository: ServableRepository[F],
    appRepo: ApplicationRepository[F],
    versionRepository: ModelVersionRepository[F],
    monitor: ServableMonitor[F],
    servableDH: ServablePublisher[F]
  ): ServableService[F] = new ServableService[F] {

    override def deploy(modelVersion: ModelVersion): F[DeferredResult[F, GenericServable]] = {
      for {
        randomSuffix <- generateUniqueSuffix(modelVersion)
        d <- Deferred[F, GenericServable]
        initServable = Servable(modelVersion, randomSuffix, Servable.Starting("Initialization", None, None), Nil)
        _ <- servableRepository.upsert(initServable)
        _ <- awaitServable(initServable)
          .flatMap(d.complete)
          .onError {
            case NonFatal(ex) =>
              cloudDriver.remove(initServable.fullName).attempt >>
                d.complete(initServable.copy(status = Servable.NotServing(ex.getMessage, None, None))).attempt >>
                F.delay(logger.error(ex))
          }
          .start
      } yield DeferredResult(initServable, d)
    }

    def awaitServable(servable: GenericServable): F[GenericServable] = {
      for {
        _ <- cloudDriver.run(servable.fullName, servable.modelVersion.id, servable.modelVersion.image, servable.modelVersion.hostSelector)
        servableDef <- monitor.monitor(servable)
        resultServable <- servableDef.get
        _ <- F.delay(logger.debug(s"Servable init finished ${resultServable.fullName}"))
        _ <- servableDH.update(resultServable)
      } yield resultServable
    }

    override def stop(name: String): F[GenericServable] = {
      for {
        servable <- OptionT(servableRepository.get(name))
          .getOrElseF(F.raiseError(DomainError.notFound(s"Can't stop Servable $name because it doesn't exist")))
        apps <- appRepo.findServableUsage(name)
        _ <- apps match {
          case Nil =>
            servableDH.remove(name) >>
              cloudDriver.remove(name) >>
              servableRepository.delete(name).void
          case usedApps =>
            val appNames = usedApps.map(_.name)
            F.raiseError[Unit](DomainError.invalidRequest(s"Can't delete servable $name. It's used by $appNames apps."))
        }
      } yield servable
    }

    override def findAndDeploy(name: String, version: Long): F[DeferredResult[F, GenericServable]] = {
      for {
        version <- OptionT(versionRepository.get(name, version))
          .getOrElseF(F.raiseError(DomainError.notFound(s"Model $name:$version doesn't exist")))
        servable <- deploy(version)
      } yield servable
    }

    override def findAndDeploy(modelId: Long): F[DeferredResult[F, GenericServable]] = {
      for {
        version <- OptionT(versionRepository.get(modelId))
          .getOrElseF(F.raiseError(DomainError.notFound(s"Model id=$modelId doesn't exist")))
        servable <- deploy(version)
      } yield servable
    }

    def generateUniqueSuffix(mv: ModelVersion): F[String] = {
      def _gen(tries: Long): F[String] = {
        for {
          randomSuffix <- nameGenerator.getName()
          fullName = Servable.fullName(mv.model.name, mv.modelVersion, randomSuffix)
          maybeServable <- servableRepository.get(fullName)
          res <- maybeServable match {
            case Some(_) if tries > 3 => idGenerator.generate().map(_.toString)
            case Some(_) => _gen(tries + 1) // name exists. try again
            case None => randomSuffix.pure[F]
          }
        } yield res
      }

      _gen(0)
    }

    override def all(): F[List[GenericServable]] = servableRepository.all()
  }
}