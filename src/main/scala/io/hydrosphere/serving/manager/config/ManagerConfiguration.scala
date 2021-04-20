package io.hydrosphere.serving.manager.config

import cats.Show
import cats.effect.Sync
import cats.syntax.either._
import com.amazonaws.regions.Regions
import io.circe.parser._
import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfiguration
import pureconfig.error.CannotConvert
import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.generic.auto._
import cats.derived.semiauto

case class ManagerConfiguration(
    application: ApplicationConfig,
    database: HikariConfiguration,
    cloudDriver: CloudDriverConfiguration,
    dockerRepository: DockerRepositoryConfiguration,
    defaultDeploymentConfiguration: DeploymentConfiguration = DeploymentConfiguration.empty
)

object ManagerConfiguration {
  implicit val show: Show[ManagerConfiguration] = {
    implicit val depConfShow: Show[DeploymentConfiguration] = Show.fromToString
    semiauto.show
  }
  implicit val depConfigReader: ConfigReader[DeploymentConfiguration] =
    ConfigReader
      .fromString { str =>
        decode[DefaultDeploymentConfiguration](str).leftMap { err =>
          CannotConvert(str, classOf[DefaultDeploymentConfiguration].getSimpleName, err.getMessage)
        }
      }
      .map(_.toDC)

  implicit val regionsConfigReader: ConfigReader[Regions] = ConfigReader.fromString { str =>
    Either
      .catchNonFatal(Regions.fromName(str))
      .leftMap(e => CannotConvert(str, "Region", e.getMessage))
  }

  def load[F[_]](
      configSource: ConfigSource = ConfigSource.default
  )(implicit F: Sync[F]): F[ManagerConfiguration] =
    F.delay {
      configSource.loadOrThrow[ManagerConfiguration]
    }
}
