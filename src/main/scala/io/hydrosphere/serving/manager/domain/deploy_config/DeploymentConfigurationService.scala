package io.hydrosphere.serving.manager.domain.deploy_config

import cats.MonadError
import cats.data.OptionT
import cats.implicits._
import io.hydrosphere.serving.manager.domain.DomainError

trait DeploymentConfigurationService[F[_]] {
  def all(): F[List[DeploymentConfiguration]]

  def create(deploymentConfiguration: DeploymentConfiguration): F[DeploymentConfiguration]

  def delete(name: String): F[DeploymentConfiguration]

  def get(name: String): F[Option[DeploymentConfiguration]]
}

object DeploymentConfigurationService {
  def apply[F[_]](hsRepo: DeploymentConfigurationRepository[F])(implicit F: MonadError[F, Throwable]): DeploymentConfigurationService[F] = new DeploymentConfigurationService[F] {

    def create(dc: DeploymentConfiguration): F[DeploymentConfiguration] =
      hsRepo.get(dc.name).flatMap {
        case Some(_) => F.raiseError(DomainError.invalidRequest(s"DeploymentConfiguration ${dc.name} already exists"))
        case None => hsRepo.create(dc)
      }

    override def get(name: String): F[Option[DeploymentConfiguration]] = hsRepo.get(name)

    override def delete(name: String): F[DeploymentConfiguration] =
      for {
        dc <- OptionT(get(name)).getOrElseF(F.raiseError(DomainError.notFound(s"Can't find DeploymentConfiguration with name=$name")))
        _ <- hsRepo.delete(dc.name)
      } yield dc

    override def all(): F[List[DeploymentConfiguration]] = hsRepo.all()
  }
}