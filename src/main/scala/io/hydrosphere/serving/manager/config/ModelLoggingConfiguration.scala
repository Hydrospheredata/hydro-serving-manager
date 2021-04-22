package io.hydrosphere.serving.manager.config

import cats.Show
import cats.derived.semiauto

case class ModelLoggingConfiguration(
    driver: String,
    params: Map[String, String]
)

object ModelLoggingConfiguration {
  implicit val show: Show[ModelLoggingConfiguration] = semiauto.show
}
