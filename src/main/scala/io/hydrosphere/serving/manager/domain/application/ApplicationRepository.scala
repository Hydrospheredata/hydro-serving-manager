package io.hydrosphere.serving.manager.domain.application

import io.hydrosphere.serving.manager.infrastructure.db.repository.DBApplicationRepository.ApplicationRow

trait ApplicationRepository[F[_]] {
  def create(entity: Application): F[Application]

  def get(id: Long): F[Option[Application]]

  def get(name: String): F[Option[Application]]

  def update(value: Application): F[Int]

  def updateRow(row: ApplicationRow): F[Int]  // NB: not good, need to provide low-level API for Application tool

  def delete(id: Long): F[Int]

  def all(): fs2.Stream[F, Application]

  def findVersionUsage(versionIdx: Long): fs2.Stream[F, Application]

  def findServableUsage(servableName: String): fs2.Stream[F, Application]
}
