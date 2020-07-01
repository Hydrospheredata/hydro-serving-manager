package io.hydrosphere.serving.manager.domain.deploy_config

import cats.Monad
import cats.data.EitherT
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.hydrosphere.serving.manager.domain.DomainError

trait HostSelectorService[F[_]] {
  def all(): F[List[DeploymentConfiguration]]

  def create(name: String, nodeSelector: Map[String, String]): F[Either[DomainError, DeploymentConfiguration]]

  def delete(name: String): F[Either[DomainError, DeploymentConfiguration]]

  def get(name: String): F[Either[DomainError, DeploymentConfiguration]]
}

object HostSelectorService {
  def apply[F[_] : Monad](hsRepo: HostSelectorRepository[F]): HostSelectorService[F] = new HostSelectorService[F] {

    def create(name: String, nodeSelector: Map[String, String]): F[Either[DomainError, DeploymentConfiguration]] = {
      hsRepo.get(name).flatMap {
        case Some(_) => Monad[F].pure(Left(DomainError.invalidRequest(s"HostSelector $name already exists")))
        case None =>
          val environment = DeployConfiguration(
            name = name,
            nodeSelector = nodeSelector,
            id = 0L
          )
          hsRepo.create(environment).map(Right(_))
      }
    }

    override def get(name: String): F[Either[DomainError, DeploymentConfiguration]] = {
      val f = for {
        hs <- EitherT.fromOptionF(hsRepo.get(name), DomainError.notFound(s"Can't find HostSelector with name $name"))
      } yield hs
      f.value
    }

    override def delete(name: String): F[Either[DomainError, DeploymentConfiguration]] = {
      val f = for {
        hs <- EitherT.fromOptionF[F, DomainError, DeploymentConfiguration](hsRepo.get(name), DomainError.notFound(s"Can't find HostSelector with name $name"))
        _ <- EitherT.liftF[F, DomainError, Int](hsRepo.delete(hs.id))
      } yield hs
      f.value
    }

    override def all(): F[List[DeploymentConfiguration]] = hsRepo.all()
  }
}