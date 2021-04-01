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

object ServableRepository {
  def make[F[_]](defaultDC: Option[DeploymentConfiguration])(implicit
      F: Bracket[F, Throwable],
      tx: Transactor[F],
      servablePub: ServableEvents.Publisher[F]
  ): ServableRepository[F] = {
    val dbRepo = DBServableRepository.make[F]()
    defaultDC match {
      case Some(value) => withDefaultDepConfig(dbRepo, value)
      case None        => dbRepo
    }
  }

  def withDefaultDepConfig[F[_]](
      sRepo: ServableRepository[F],
      defaultDC: DeploymentConfiguration
  ): ServableRepository[F] =
    new ServableRepository[F] {
      override def all(): F[List[Servable]] = sRepo.all()

      override def upsert(servable: Servable): F[Servable] =
        if (servable.deploymentConfiguration.exists(_.name == defaultDC.name))
          sRepo.upsert(servable.copy(deploymentConfiguration = None))
        else
          sRepo.upsert(servable)

      override def delete(name: String): F[Int] = sRepo.delete(name)

      override def get(name: String): F[Option[Servable]] = sRepo.get(name)

      override def get(names: Seq[String]): F[List[Servable]] = sRepo.get(names)

      override def findForModelVersion(versionId: Long): F[List[Servable]] =
        sRepo.findForModelVersion(versionId)
    }
}
