package io.hydrosphere.serving.manager.domain.deploy_config

import cats.MonadError
import cats.effect.Bracket
import io.hydrosphere.serving.manager.domain.DomainError
import cats.implicits._
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBDeploymentConfigurationRepository

trait DeploymentConfigurationRepository[F[_]] {
  def create(entity: DeploymentConfiguration): F[DeploymentConfiguration]

  def get(name: String): F[Option[DeploymentConfiguration]]

  def all(): F[List[DeploymentConfiguration]]

  def delete(name: String): F[Int]
}

object DeploymentConfigurationRepository {
  def make[F[_]](defaultDC: DeploymentConfiguration)(implicit
      F: Bracket[F, Throwable],
      tx: Transactor[F],
      pub: DeploymentConfigurationEvents.Publisher[F]
  ): DeploymentConfigurationRepository[F] = {
    val dbRepo = new DBDeploymentConfigurationRepository[F]()
    withDefaultDepConfig(dbRepo, defaultDC)
  }

  def withDefaultDepConfig[F[_]](
      repo: DeploymentConfigurationRepository[F],
      defaultDC: DeploymentConfiguration
  )(implicit F: MonadError[F, Throwable]): DeploymentConfigurationRepository[F] =
    new DeploymentConfigurationRepository[F] {
      override def create(entity: DeploymentConfiguration): F[DeploymentConfiguration] =
        if (entity.name == defaultDC.name)
          F.raiseError(DomainError.invalidRequest(s"${entity.name} already exists."))
        else
          repo.create(entity)

      override def get(name: String): F[Option[DeploymentConfiguration]] =
        if (name == defaultDC.name)
          defaultDC.some.pure[F]
        else
          repo.get(name)

      override def all(): F[List[DeploymentConfiguration]] =
        repo.all().map(defaultDC +: _)

      override def delete(name: String): F[Int] =
        if (name == defaultDC.name)
          F.raiseError(
            DomainError.invalidRequest(s"Can't delete default deployment configuration $name")
          )
        else
          repo.delete(name)
    }
}
