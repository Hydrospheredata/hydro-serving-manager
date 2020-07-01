package io.hydrosphere.serving.manager.infrastructure.db.repository

import cats.effect.Bracket
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.domain.deploy_config.{DeploymentConfiguration, DeploymentConfigurationRepository}
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBDeploymentConfigurationRepository._

class DBDeploymentConfigurationRepository[F[_]]()(implicit F: Bracket[F, Throwable], tx: Transactor[F]) extends DeploymentConfigurationRepository[F] {
  override def create(entity: DeploymentConfiguration): F[DeploymentConfiguration] = {
    insertQ(entity).run.transact(tx).as(entity)
  }

  override def get(name: String): F[Option[DeploymentConfiguration]] = {
    getByNameQ(name).option.transact(tx)
  }

  override def all(): F[List[DeploymentConfiguration]] = {
    allQ.to[List].transact(tx)
  }

  override def delete(name: String): F[Int] = {
    deleteQ(name).run.transact(tx)
  }
}

object DBDeploymentConfigurationRepository {

  def allQ: doobie.Query0[DeploymentConfiguration] = sql"SELECT * FROM hydro_serving.deployment_configuration".query[DeploymentConfiguration]

  def insertQ(entity: DeploymentConfiguration): doobie.Update0 = sql"""
    |INSERT INTO hydro_serving.deployment_configuration (
    | name,
    | container,
    | pod,
    | deployment,
    | hpa
    |) VALUES (
    | ${entity.name},
    | ${entity.container},
    | ${entity.pod},
    | ${entity.deployment},
    | ${entity.hpa},
    |)""".stripMargin.update

  def getByNameQ(name: String): doobie.Query0[DeploymentConfiguration] = sql"SELECT * FROM hydro_serving.deployment_configuration WHERE name = $name".query[DeploymentConfiguration]

  def deleteQ(name: String): doobie.Update0 = sql"DELETE FROM hydro_serving.deployment_configuration WHERE name = $name".update
}