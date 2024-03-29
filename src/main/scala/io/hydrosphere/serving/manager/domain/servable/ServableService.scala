package io.hydrosphere.serving.manager.domain.servable

import cats.data.OptionT
import cats.effect._
import cats.implicits._
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.application.ApplicationRepository
import io.hydrosphere.serving.manager.domain.clouddriver._
import io.hydrosphere.serving.manager.domain.deploy_config.{
  DeploymentConfiguration,
  DeploymentConfigurationService
}
import io.hydrosphere.serving.manager.domain.model_version.{
  ModelVersion,
  ModelVersionRepository,
  ModelVersionStatus
}
import io.hydrosphere.serving.manager.domain.monitoring.{Monitoring, MonitoringRepository}
import io.hydrosphere.serving.manager.util.UUIDGenerator
import io.hydrosphere.serving.manager.util.random.NameGenerator
import org.apache.logging.log4j.scala.Logging

trait ServableService[F[_]] {
  def get(name: String): F[Servable]

  def all(): F[List[Servable]]

  def getFiltered(
      name: Option[String],
      versionId: Option[Long],
      metadata: Map[String, String]
  ): F[List[Servable]]

  def findAndDeploy(
      name: String,
      version: Long,
      deployConfigName: Option[String],
      metadata: Map[String, String]
  ): F[Servable]

  def findAndDeploy(
      modelId: Long,
      deployConfigName: Option[String],
      metadata: Map[String, String]
  ): F[Servable]

  def stop(name: String): F[Servable]

  def deploy(
      modelVersion: ModelVersion.Internal,
      deployConfig: Option[DeploymentConfiguration],
      metadata: Map[String, String]
  ): F[Servable]
}

object ServableService extends Logging {
  type ServableFiler = List[Servable] => List[Servable]

  def filterByName(name: String): ServableFiler =
    _.filter(_.name == name)

  def filterByVersionId(versionId: Long): ServableFiler =
    _.filter(_.modelVersion.id == versionId)

  def filterByMetadata(metadata: Map[String, String]): ServableFiler =
    _.filter(s => s.metadata.toSet.subsetOf(metadata.toSet))

  def apply[F[_]](
      defaultDC: DeploymentConfiguration
  )(implicit
      F: Concurrent[F],
      timer: Timer[F],
      nameGenerator: NameGenerator[F],
      idGenerator: UUIDGenerator[F],
      cloudDriver: CloudDriver[F],
      servableRepository: ServableRepository[F],
      appRepo: ApplicationRepository[F],
      versionRepository: ModelVersionRepository[F],
      monitoringRepository: MonitoringRepository[F],
      deploymentConfigService: DeploymentConfigurationService[F]
  ): ServableService[F] =
    new ServableService[F] {
      override def all(): F[List[Servable]] =
        servableRepository.all()

      override def getFiltered(
          name: Option[String],
          versionId: Option[Long],
          metadata: Map[String, String]
      ): F[List[Servable]] = {
        val maybeMetadata = if (metadata.nonEmpty) metadata.some else None
        val filtersMaybe = name.map(filterByName) :: versionId.map(
          filterByVersionId
        ) :: maybeMetadata.map(filterByMetadata) :: Nil
        val filters = filtersMaybe.flatten
        val finalFilter = filters.foldLeft(identity[List[Servable]](_)) {
          case (a, b) => b.andThen(a)
        }
        for {
          servables <- servableRepository.all()
        } yield finalFilter(servables)
      }

      override def deploy(
          modelVersion: ModelVersion.Internal,
          deployConfig: Option[DeploymentConfiguration],
          metadata: Map[String, String]
      ): F[Servable] =
        for {
          _ <- modelVersion.status match {
            case ModelVersionStatus.Released => F.unit
            case x =>
              F.raiseError[Unit](
                DomainError.invalidRequest(
                  s"Can't create a Servable for a model version with status ${x}. Released status expected."
                )
              )
          }
          randomSuffix <- generateUniqueSuffix(modelVersion)
          initServable = Servable(
            modelVersion = modelVersion,
            name =
              Servable.fullName(modelVersion.model.name, modelVersion.modelVersion, randomSuffix),
            status = Servable.Status.Starting,
            usedApps = Nil,
            message = "Initialization".some,
            metadata = metadata,
            host = None,
            port = None,
            deploymentConfiguration = deployConfig.getOrElse(defaultDC)
          )
          servable <- servableRepository.upsert(initServable)
          newServable <-
            cloudDriver
              .run(
                servable.name,
                servable.modelVersion.id,
                servable.modelVersion.image,
                servable.deploymentConfiguration
              )
              .map(ci => servable.copy(host = ci.host, port = ci.port))
              .handleError(e =>
                servable.copy(status = Servable.Status.NotServing, message = e.getMessage.some)
              )
          updatedServable <- servableRepository.upsert(newServable)
        } yield updatedServable

      override def stop(name: String): F[Servable] =
        for {
          servable <- OptionT(servableRepository.get(name))
            .getOrElseF(
              F.raiseError(
                DomainError.notFound(s"Can't stop Servable $name because it doesn't exist")
              )
            )

          metricSpec <- servable.metadata.get(Monitoring.MetricSpecIdKey).flatTraverse {
            metricSpecId => monitoringRepository.get(metricSpecId)
          }
          _ <- metricSpec match {
            case Some(_) =>
              val error = DomainError.invalidRequest(
                s"Can't delete servable because it's used by MetricSpec ${metricSpec}"
              )
              F.raiseError[Unit](error)
            case None => F.unit
          }

          apps <- appRepo.findServableUsage(name)
          _ <- apps match {
            case Nil =>
              cloudDriver.remove(name) >>
                servableRepository.delete(name).void
            case usedApps =>
              val appNames = usedApps.map(_.name)
              F.raiseError[Unit](
                DomainError
                  .invalidRequest(s"Can't delete servable $name. It's used by $appNames apps.")
              )
          }
        } yield servable

      override def findAndDeploy(
          name: String,
          version: Long,
          configName: Option[String],
          metadata: Map[String, String]
      ): F[Servable] =
        for {
          abstractVersion <- OptionT(versionRepository.get(name, version))
            .getOrElseF(F.raiseError(DomainError.notFound(s"Model $name:$version doesn't exist")))
          internalVersion <- abstractVersion match {
            case x: ModelVersion.Internal =>
              x.pure[F]
            case x: ModelVersion.External =>
              DomainError
                .invalidRequest(
                  s"Deployment of external model is unavailable. modelVersionId=${x.id} name=${x.fullName}"
                )
                .raiseError[F, ModelVersion.Internal]
          }
          maybeConfig <- configName.traverse(deploymentConfigService.get)
          servable    <- deploy(internalVersion, maybeConfig, metadata)
        } yield servable

      override def findAndDeploy(
          modelId: Long,
          configName: Option[String],
          metadata: Map[String, String]
      ): F[Servable] =
        for {
          abstractVersion <- OptionT(versionRepository.get(modelId))
            .getOrElseF(F.raiseError(DomainError.notFound(s"Model id=$modelId doesn't exist")))
          internalVersion <- abstractVersion match {
            case x: ModelVersion.Internal =>
              x.pure[F]
            case x: ModelVersion.External =>
              DomainError
                .invalidRequest(
                  s"Deployment of external model is unavailable. modelVersionId=${x.id} name=${x.fullName}"
                )
                .raiseError[F, ModelVersion.Internal]
          }
          maybeConfig <- configName.traverse(deploymentConfigService.get)
          servable    <- deploy(internalVersion, maybeConfig, metadata)
        } yield servable

      def generateUniqueSuffix(mv: ModelVersion.Internal): F[String] = {
        def _gen(tries: Long): F[String] =
          for {
            randomSuffix <- nameGenerator.getName()
            fullName = Servable.fullName(mv.model.name, mv.modelVersion, randomSuffix)
            maybeServable <- servableRepository.get(fullName)
            res <- maybeServable match {
              case Some(_) if tries > 3 => idGenerator.generate().map(_.toString)
              case Some(_)              => _gen(tries + 1) // name exists. try again
              case None                 => randomSuffix.pure[F]
            }
          } yield res

        _gen(0)
      }

      override def get(name: String): F[Servable] =
        OptionT(servableRepository.get(name))
          .getOrElseF(F.raiseError(DomainError.notFound(s"Can't find Servable with name=${name}")))
    }
}
