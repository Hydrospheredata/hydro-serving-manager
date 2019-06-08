package io.hydrosphere.serving.manager.infrastructure.docker

case class DockerRegistryAuth(
  username: Option[String],
  password: Option[String],
  email: Option[String],
  serverAddress: Option[String],
  identityToken: Option[String],
  auth: Option[String]
)
