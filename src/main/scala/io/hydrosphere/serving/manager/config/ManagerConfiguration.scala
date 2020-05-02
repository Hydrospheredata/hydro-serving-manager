package io.hydrosphere.serving.manager.config

import java.nio.file.Path

import cats.effect.Sync
import cats.syntax.either._
import com.amazonaws.regions.Regions
import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.error.CannotConvert
import pureconfig.generic.auto._

case class ManagerConfiguration(
  application: ApplicationConfig,
  localStorage: Option[Path],
  database: HikariConfiguration,
  cloudDriver: CloudDriverConfiguration,
  dockerRepository: DockerRepositoryConfiguration,
)

object ManagerConfiguration {
  implicit val regionsConfigReader: ConfigReader[Regions] = ConfigReader.fromString { str =>
    Either.catchNonFatal(Regions.fromName(str))
      .leftMap(e => CannotConvert(str, "Region", e.getMessage))
  }

  def load[F[_]](implicit F: Sync[F]): F[ManagerConfiguration] = F.delay {
    ConfigSource.default.loadOrThrow[ManagerConfiguration]
  }
}