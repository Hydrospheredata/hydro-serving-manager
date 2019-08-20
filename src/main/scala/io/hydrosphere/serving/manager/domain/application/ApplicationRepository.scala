package io.hydrosphere.serving.manager.domain.application

import io.hydrosphere.serving.manager.domain.application.Application.GenericApplication

trait ApplicationRepository[F[_]] {
  def create(entity: GenericApplication): F[GenericApplication]

  def get(id: Long): F[Option[GenericApplication]]

  def get(name: String): F[Option[GenericApplication]]

  def update(value: GenericApplication): F[Int]

  def delete(id: Long): F[Int]

  def all(): F[List[GenericApplication]]

  def findVersionUsage(versionIdx: Long): F[List[GenericApplication]]

  def findServableUsage(servableName: String): F[List[GenericApplication]]
}
