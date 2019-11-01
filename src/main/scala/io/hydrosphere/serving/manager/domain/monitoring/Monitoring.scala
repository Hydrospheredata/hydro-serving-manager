package io.hydrosphere.serving.manager.domain.monitoring

import cats.MonadError
import cats.data.OptionT
import cats.implicits._
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionRepository}
import io.hydrosphere.serving.manager.domain.servable.ServableService
import org.apache.logging.log4j.scala.Logging


trait Monitoring[F[_]] {
  def create(spec: CustomModelMetricSpec): F[CustomModelMetricSpec]

  def delete(specId: String): F[Unit]

  def update(spec: CustomModelMetricSpec): F[CustomModelMetricSpec]
}


object Monitoring extends Logging {

  final val MetricSpecIdKey = "metric-spec-id"

  case class NoMonitoringModelFound(spec: CustomModelMetricSpec) extends RuntimeException(s"Can't find a model version with id ${spec.modelVersionId} for metric spec ${spec.id}")

  def apply[F[_]]()(
    implicit
    F: MonadError[F, Throwable],
    repo: MonitoringRepository[F],
    servableService: ServableService[F],
    versionRepo: ModelVersionRepository[F],
    pub: MetricSpecEvents.Publisher[F]
  ): Monitoring[F] = new Monitoring[F] {
    override def create(spec: CustomModelMetricSpec): F[CustomModelMetricSpec] = {
      for {
        mvMonitor <- OptionT(versionRepo.get(spec.config.modelVersionId))
          .getOrElseF(NoMonitoringModelFound(spec).raiseError[F, ModelVersion])
        mvTarget <- OptionT(versionRepo.get(spec.modelVersionId))
          .getOrElseF(NoMonitoringModelFound(spec).raiseError[F, ModelVersion])
        servableMetadata = Map(
          MetricSpecIdKey -> spec.id,
          "metric-spec-name" -> spec.name,
          "metric-spec-target-id" -> mvTarget.id.toString,
          "metric-spec-target-name" -> mvTarget.fullName
        )
        monitorServable <- servableService.deploy(mvMonitor, servableMetadata)
        deployedSpec = spec.copy(config = spec.config.copy(servable = monitorServable.started.some))
        _ <- repo.upsert(deployedSpec)
        _ <- pub.update(deployedSpec)
      } yield spec
    }

    override def delete(specId: String): F[Unit] = {
      for {
        spec <- OptionT(repo.get(specId)).getOrElseF(F.raiseError(DomainError.NotFound(s"MetricSpec with id ${specId} not found")))
        _ <- repo.delete(specId)
        _ <- spec.config.servable.traverse { servable =>
          logger.debug(s"Servable ${servable.fullName} is attached to MetricSpec ${specId}. Deleting...")
          servableService.stop(servable.fullName)
        }
        _ = logger.debug("Send MetricSpec remove event")
        _ <- pub.remove(specId)
      } yield ()
    }

    override def update(spec: CustomModelMetricSpec): F[CustomModelMetricSpec] = {
      for {
        _ <- repo.upsert(spec)
        _ <- pub.update(spec)
      } yield spec
    }
  }
}