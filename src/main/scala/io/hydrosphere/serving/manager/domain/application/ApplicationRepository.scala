package io.hydrosphere.serving.manager.domain.application

import io.hydrosphere.serving.manager.db.Tables
import io.hydrosphere.serving.manager.domain.application.Application.GenericApplication
import io.hydrosphere.serving.manager.domain.servable.Servable.GenericServable

trait ApplicationRepository[F[_]] {
  def create(entity: GenericApplication): F[GenericApplication]

  def get(id: Long): F[Option[GenericApplication]]

  def get(name: String): F[Option[GenericApplication]]

  def update(value: GenericApplication): F[Int]

  def updateRow(row: Tables.ApplicationRow): F[Int]

  def delete(id: Long): F[Int]

  def all(): F[List[GenericApplication]]

  def applicationsWithCommonServices(servables: Set[GenericServable], applicationId: Long): F[List[GenericApplication]]

  def findVersionsUsage(versionIdx: Long): F[List[GenericApplication]]
}
