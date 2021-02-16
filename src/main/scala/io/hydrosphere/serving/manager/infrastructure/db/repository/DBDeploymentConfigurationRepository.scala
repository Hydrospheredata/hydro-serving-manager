package io.hydrosphere.serving.manager.infrastructure.db.repository

import cats.data.NonEmptyList
import cats.effect.Bracket
import cats.implicits._
import doobie.Fragments
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.domain.deploy_config._
import io.hydrosphere.serving.manager.infrastructure.db.Metas.jsonCodecMeta
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBDeploymentConfigurationRepository._

class DBDeploymentConfigurationRepository[F[_]]()(implicit
    F: Bracket[F, Throwable],
    tx: Transactor[F],
    pub: DeploymentConfigurationEvents.Publisher[F]
) extends DeploymentConfigurationRepository[F] {
  override def create(entity: DeploymentConfiguration): F[DeploymentConfiguration] =
    insertQ(entity).run.transact(tx).as(entity).flatTap(pub.update)

  override def get(name: String): F[Option[DeploymentConfiguration]] =
    getByNameQ(name).option.transact(tx)

  override def all(): F[List[DeploymentConfiguration]] =
    allQ.to[List].transact(tx)

  override def delete(name: String): F[Int] =
    deleteQ(name).run.transact(tx).flatTap(_ => pub.remove(name))
}

object DBDeploymentConfigurationRepository {
  implicit val containerMeta  = jsonCodecMeta[K8sContainerConfig]
  implicit val podMeta        = jsonCodecMeta[K8sPodConfig]
  implicit val deploymentMeta = jsonCodecMeta[K8sDeploymentConfig]
  implicit val hpaMeta        = jsonCodecMeta[K8sHorizontalPodAutoscalerConfig]

  def allQ: doobie.Query0[DeploymentConfiguration] =
    sql"SELECT * FROM hydro_serving.deployment_configuration".query[DeploymentConfiguration]

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
                                                                      | ${entity.hpa}
                                                                      |)""".stripMargin.update

  def getByNameQ(name: String): doobie.Query0[DeploymentConfiguration] =
    sql"SELECT * FROM hydro_serving.deployment_configuration WHERE name = $name"
      .query[DeploymentConfiguration]

  def getManyQ(names: NonEmptyList[String]): doobie.Query0[DeploymentConfiguration] = {
    val frag = fr"SELECT * FROM hydro_serving.deployment_configuration WHERE " ++ Fragments.in(
      fr"name",
      names
    )
    frag.query[DeploymentConfiguration]
  }

  def deleteQ(name: String): doobie.Update0 =
    sql"DELETE FROM hydro_serving.deployment_configuration WHERE name = $name".update
}
