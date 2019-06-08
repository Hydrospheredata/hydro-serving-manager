package io.hydrosphere.serving.manager.infrastructure.docker

object DockerClientHelper {
  def createRegistryAuth(registryAuth: DockerRegistryAuth): com.spotify.docker.client.messages.RegistryAuth = {
    com.spotify.docker.client.messages.RegistryAuth.create(
      registryAuth.username.orNull,
      registryAuth.password.orNull,
      registryAuth.email.orNull,
      registryAuth.serverAddress.orNull,
      registryAuth.identityToken.orNull,
      registryAuth.auth.orNull
    )
  }
}
