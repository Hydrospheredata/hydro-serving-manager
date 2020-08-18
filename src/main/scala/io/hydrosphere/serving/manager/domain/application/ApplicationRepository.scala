package io.hydrosphere.serving.manager.domain.application

import io.hydrosphere.serving.manager.infrastructure.db.repository.DBApplicationRepository.ApplicationRow

trait ApplicationRepository[F[_]] {
  def create(entity: Application): F[Application]

  def get(id: Long): F[Option[Application]]

  def get(name: String): F[Option[Application]]

  def update(value: Application): F[Int]

  def delete(id: Long): F[Int]

  def all(): F[List[Application]]

  def findVersionUsage(versionIdx: Long): F[List[Application]]

  def findServableUsage(servableName: String): F[List[Application]]
}
