package io.hydrosphere.serving.manager.domain.model_version

trait ModelVersionRepository[F[_]] {
  def create(entity: ModelVersion): F[ModelVersion]

  def update(entity: ModelVersion): F[Int]

  def get(id: Long): F[Option[ModelVersion]]

  def get(modelName: String, modelVersion: Long): F[Option[ModelVersion]]

  def delete(id: Long): F[Int]

  def all(): F[List[ModelVersion]]

  def listForModel(modelId: Long): F[List[ModelVersion]]

  def lastModelVersionByModel(modelId: Long): F[Option[ModelVersion]]
}