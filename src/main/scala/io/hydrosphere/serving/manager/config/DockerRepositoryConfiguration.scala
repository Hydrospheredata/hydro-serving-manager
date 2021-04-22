package io.hydrosphere.serving.manager.config

import cats.Show
import cats.derived.semiauto
import com.amazonaws.regions.Regions
import io.hydrosphere.serving.manager.util.Secret

sealed trait DockerRepositoryConfiguration

object DockerRepositoryConfiguration {
  case object Local extends DockerRepositoryConfiguration

  case class Ecs(
      region: Regions,
      accountId: String
  ) extends DockerRepositoryConfiguration

  case class Remote(
      host: String,
      username: Option[String],
      password: Option[Secret[String]],
      pullHost: Option[String],
      imagePrefix: Option[String]
  ) extends DockerRepositoryConfiguration

  implicit val regions: Show[Regions]                  = Show.fromToString
  implicit val ecs: Show[Ecs]                          = semiauto.show
  implicit val remote: Show[Remote]                    = semiauto.show
  implicit val dr: Show[DockerRepositoryConfiguration] = semiauto.show
}
