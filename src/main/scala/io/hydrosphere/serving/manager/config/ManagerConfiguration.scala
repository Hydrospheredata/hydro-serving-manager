package io.hydrosphere.serving.manager.config

import java.nio.file.Path
import cats.effect.Sync
import cats.syntax.either._
import com.amazonaws.regions.Regions
import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfiguration
import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.error.CannotConvert
import pureconfig.generic.auto._
import io.circe.parser._

case class ManagerConfiguration(
    application: ApplicationConfig,
    localStorage: Option[Path],
    database: HikariConfiguration,
    cloudDriver: CloudDriverConfiguration,
    dockerRepository: DockerRepositoryConfiguration,
    defaultDeploymentConfiguration: Option[DeploymentConfiguration]
)

object ManagerConfiguration {
  implicit val depConfigReader: ConfigReader[DeploymentConfiguration] = ConfigReader.fromString {
    str =>
      decode[DeploymentConfiguration](str).leftMap { err =>
        CannotConvert(str, classOf[DeploymentConfiguration].getSimpleName, err.getMessage)
      }
  }

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
