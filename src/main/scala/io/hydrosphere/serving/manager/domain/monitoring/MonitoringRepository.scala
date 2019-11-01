package io.hydrosphere.serving.manager.domain.monitoring


trait MonitoringRepository[F[_]] {
  def all(): F[List[CustomModelMetricSpec]]

  def get(id: String): F[Option[CustomModelMetricSpec]]

  def forModelVersion(id: Long): F[List[CustomModelMetricSpec]]

  def upsert(spec: CustomModelMetricSpec): F[Unit]

  def delete(id: String): F[Unit]
}
