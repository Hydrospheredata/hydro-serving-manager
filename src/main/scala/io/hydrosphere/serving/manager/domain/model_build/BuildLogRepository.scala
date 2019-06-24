package io.hydrosphere.serving.manager.domain.model_build

trait BuildLogRepository[F[_]] {
  def add(modelVersionId: Long, logs: List[String]): F[Unit]
  def get(modelVersionId: Long): F[Option[fs2.Stream[F,String]]]
}
