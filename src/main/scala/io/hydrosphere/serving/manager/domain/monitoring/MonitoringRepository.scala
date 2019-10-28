package io.hydrosphere.serving.manager.domain.monitoring


trait MonitoringRepository[F[_]] {
  def all(): F[List[CustomModelMetricSpec]]

  def get(id: String): F[Option[CustomModelMetricSpec]]

  def insert(spec: CustomModelMetricSpec): F[Unit]

  def delete(id: String): F[Unit]
}
