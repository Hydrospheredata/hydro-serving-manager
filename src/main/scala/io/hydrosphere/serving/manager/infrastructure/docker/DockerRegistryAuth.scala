package io.hydrosphere.serving.manager.infrastructure.docker

import com.spotify.docker.client.messages.RegistryAuth

case class DockerRegistryAuth(
  username: Option[String],
  password: Option[String],
  email: Option[String],
  serverAddress: Option[String],
  identityToken: Option[String],
  auth: Option[String]
) {
  def inderlying: RegistryAuth = {
    RegistryAuth.create(
      username.orNull,
      password.orNull,
      email.orNull,
      serverAddress.orNull,
      identityToken.orNull,
      auth.orNull
    )
  }
}
