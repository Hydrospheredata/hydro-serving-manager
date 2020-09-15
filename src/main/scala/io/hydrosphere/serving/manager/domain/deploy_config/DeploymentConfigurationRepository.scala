package io.hydrosphere.serving.manager.domain.deploy_config

trait DeploymentConfigurationRepository[F[_]] {
  def create(entity: DeploymentConfiguration): F[DeploymentConfiguration]

  def get(name: String): F[Option[DeploymentConfiguration]]

  def all(): F[List[DeploymentConfiguration]]

  def delete(name: String): F[Int]
}