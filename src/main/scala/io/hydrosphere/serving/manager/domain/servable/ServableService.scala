package io.hydrosphere.serving.manager.domain.servable

import cats.data.OptionT
import cats.effect._
import cats.implicits._
import io.hydrosphere.serving.manager.discovery.ServablePublisher
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.application.ApplicationRepository
import io.hydrosphere.serving.manager.domain.clouddriver._
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionRepository}
import io.hydrosphere.serving.manager.util.UUIDGenerator
import io.hydrosphere.serving.manager.util.random.NameGenerator
import org.apache.logging.log4j.scala.Logging

trait ServableService[F[_]] {
  def get(name: String): F[Servable]

  def all(): F[List[Servable]]

  def getFiltered(name: Option[String], versionId: Option[Long], metadata: Map[String, String]): F[List[Servable]]

  def findAndDeploy(name: String, version: Long, metadata: Map[String, String]): F[Servable]

  def findAndDeploy(modelId: Long, metadata: Map[String, String]): F[Servable]

  def stop(name: String): F[Servable]

  def deploy(modelVersion: ModelVersion, metadata: Map[String, String]): F[Servable]

  def deploy(servable: Servable): F[Servable]
}

object ServableService extends Logging {

  def filterByName(name: String): Servable => Boolean = { x: Servable =>
    x.fullName == name
  }

  def filterByVersionId(versionId: Long): Servable => Boolean = { x: Servable =>
    x.modelVersion.id == versionId
  }

  def filterByMetadata(metadata: Map[String, String]): Servable => Boolean = { x: Servable =>
    x.metadata.toSet.subsetOf(metadata.toSet)
  }

  def apply[F[_]]()(
    implicit F: Concurrent[F],
    timer: Timer[F],
    nameGenerator: NameGenerator[F],
    idGenerator: UUIDGenerator[F],
    cloudDriver: CloudDriver[F],
    servableRepository: ServableRepository[F],
    appRepo: ApplicationRepository[F],
    versionRepository: ModelVersionRepository[F],
    servableDH: ServablePublisher[F]
  ): ServableService[F] = new ServableService[F] {

    override def all(): F[List[Servable]] = {
      servableRepository.all()
    }

    override def getFiltered(name: Option[String], versionId: Option[Long], metadata: Map[String,String]): F[List[Servable]] = {
      val maybeMetadata = if (metadata.nonEmpty) metadata.some else None
      val filtersMaybe = name.map(filterByName) :: versionId.map(filterByVersionId) :: maybeMetadata.map(filterByMetadata) :: Nil
      val filters = filtersMaybe.flatten
      for {
        all <- servableRepository.all()
      } yield all.filter(s => filters.map(f => f(s)).fold(false)((r1, r2) => r1 && r2))
    }

    override def deploy(servable: Servable): F[Servable] = {
      for {
        rServable <- servableRepository.upsert(servable)
        _ <- cloudDriver.run(rServable.fullName, rServable.modelVersion.id, rServable.modelVersion.image, rServable.modelVersion.hostSelector)
      } yield rServable
    }


    override def deploy(modelVersion: ModelVersion, metadata: Map[String, String]): F[Servable] = {
      for {
        randomSuffix <- generateUniqueSuffix(modelVersion)
        initServable = Servable(modelVersion, randomSuffix, Servable.Starting("Initialization", None, None), Nil, metadata)
        result <- deploy(initServable)
      } yield result
    }

    override def stop(name: String): F[Servable] = {
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

    override def findAndDeploy(name: String, version: Long, metadata: Map[String, String]): F[Servable] = {
      for {
        version <- OptionT(versionRepository.get(name, version))
          .getOrElseF(F.raiseError(DomainError.notFound(s"Model $name:$version doesn't exist")))
        servable <- deploy(version, metadata)
      } yield servable
    }

    override def findAndDeploy(modelId: Long, metadata: Map[String, String]): F[Servable] = {
      for {
        version <- OptionT(versionRepository.get(modelId))
          .getOrElseF(F.raiseError(DomainError.notFound(s"Model id=$modelId doesn't exist")))
        servable <- deploy(version, metadata)
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

    override def get(name: String): F[Servable] = {
      OptionT(servableRepository.get(name))
        .getOrElseF(F.raiseError(DomainError.notFound(s"Can't find Servable with name=$name")))
    }

  }
}