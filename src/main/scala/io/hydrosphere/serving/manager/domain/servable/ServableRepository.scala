package io.hydrosphere.serving.manager.domain.servable

import cats.effect.Bracket
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfiguration
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBServableRepository

trait ServableRepository[F[_]] {
  def all(): F[List[Servable]]

  def upsert(servable: Servable): F[Servable]

  def delete(name: String): F[Int]

  def get(name: String): F[Option[Servable]]

  def get(names: Seq[String]): F[List[Servable]]

  def findForModelVersion(versionId: Long): F[List[Servable]]
}
