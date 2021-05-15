package io.hydrosphere.serving.manager.domain.monitoring

import cats.MonadError
import cats.data.OptionT
import cats.implicits._
import io.hydrosphere.serving.manager.api.http.controller.MonitoringController.MonitoringRequests.MetricSpecCreationRequest
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfigurationService
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionRepository}
import io.hydrosphere.serving.manager.domain.servable.ServableService
import io.hydrosphere.serving.manager.util.UUIDGenerator
import org.apache.logging.log4j.scala.Logging

trait Monitoring[F[_]] {
  def create(spec: MetricSpecCreationRequest): F[CustomModelMetricSpec]

  def deployServable(spec: CustomModelMetricSpec): F[CustomModelMetricSpec]

  def delete(specId: String): F[CustomModelMetricSpec]

  def update(spec: CustomModelMetricSpec): F[CustomModelMetricSpec]

  def all(): F[List[CustomModelMetricSpec]]
}

object Monitoring extends Logging {

  final val MetricSpecIdKey = "metric-spec-id"

  case class NoMonitoringModelFound(spec: CustomModelMetricSpec)
      extends RuntimeException(
        s"Can't find a model version with id ${spec.modelVersionId} for metric spec"
      )

  def apply[F[_]]()(implicit
      F: MonadError[F, Throwable],
      uuid: UUIDGenerator[F],
      repo: MonitoringRepository[F],
      servableService: ServableService[F],
      versionRepo: ModelVersionRepository[F],
      depConfService: DeploymentConfigurationService[F]
  ): Monitoring[F] =
    new Monitoring[F] {
      override def create(incomingMS: MetricSpecCreationRequest): F[CustomModelMetricSpec] =
        for {
          id <- uuid.generate()

          config = CustomModelMetricSpecConfiguration(
            modelVersionId = incomingMS.config.modelVersionId,
            threshold = incomingMS.config.threshold,
            thresholdCmpOperator = incomingMS.config.thresholdCmpOperator,
            servable = None,
            deploymentConfigName = incomingMS.config.deploymentConfigName
          )
          spec = CustomModelMetricSpec(
            incomingMS.name,
            incomingMS.modelVersionId,
            config,
            id = id.toString
          )
          deployedSpec <- deployServable(spec)
        } yield deployedSpec

      override def delete(specId: String): F[CustomModelMetricSpec] =
        for {
          spec <- OptionT(repo.get(specId))
            .getOrElseF(F.raiseError(DomainError.NotFound(s"MetricSpec with id $specId not found")))
          _ <- repo.delete(specId)
          _ <- spec.config.servable.traverse { servable =>
            logger.debug(
              s"Servable ${servable.name} is attached to MetricSpec $specId. Deleting..."
            )
            servableService.stop(servable.name)
          }
          _ = logger.debug("Send MetricSpec remove event")
        } yield spec

      override def update(spec: CustomModelMetricSpec): F[CustomModelMetricSpec] =
        for {
          _ <- repo.upsert(spec)
        } yield spec

      override def all(): F[List[CustomModelMetricSpec]] =
        repo.all()

      override def deployServable(spec: CustomModelMetricSpec): F[CustomModelMetricSpec] =
        spec.config.servable match {
          case Some(_) => spec.pure[F]
          case None =>
            for {
              mvMonitor <- OptionT(versionRepo.get(spec.config.modelVersionId))
                .flatMap {
                  case x: ModelVersion.Internal => OptionT.pure(x)
                  case _: ModelVersion.External => OptionT.none[F, ModelVersion.Internal]
                }
                .getOrElseF(NoMonitoringModelFound(spec).raiseError[F, ModelVersion.Internal])
              mvTarget <- OptionT(versionRepo.get(spec.modelVersionId))
                .getOrElseF(NoMonitoringModelFound(spec).raiseError[F, ModelVersion])

              depConf <- spec.config.deploymentConfigName.traverse(depConfService.get)

              servableMetadata = Map(
                MetricSpecIdKey           -> spec.id,
                "metric-spec-name"        -> spec.name,
                "metric-spec-target-id"   -> mvTarget.id.toString,
                "metric-spec-target-name" -> mvTarget.fullName
              )
              monitorServable <- servableService.deploy(mvMonitor, depConf, servableMetadata)
              deployedSpec = spec.copy(config = spec.config.copy(servable = monitorServable.some))
              _ <- repo.upsert(deployedSpec)
            } yield deployedSpec
        }
    }
}
