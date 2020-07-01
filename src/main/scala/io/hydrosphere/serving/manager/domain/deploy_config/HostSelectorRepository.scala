package io.hydrosphere.serving.manager.domain.deploy_config

trait HostSelectorRepository[F[_]] {
  def create(entity: DeploymentConfiguration): F[DeploymentConfiguration]

  def get(id: Long): F[Option[DeploymentConfiguration]]

  def get(name: String): F[Option[DeploymentConfiguration]]

  def all(): F[List[DeploymentConfiguration]]

  def delete(id: Long): F[Int]
}