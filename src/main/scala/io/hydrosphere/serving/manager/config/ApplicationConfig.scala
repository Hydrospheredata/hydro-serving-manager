package io.hydrosphere.serving.manager.config

import cats.Show
import cats.derived.semiauto

case class ApplicationConfig(
    port: Int,
    grpcPort: Int
)

object ApplicationConfig {
  implicit val appConf: Show[ApplicationConfig] = semiauto.show
}
