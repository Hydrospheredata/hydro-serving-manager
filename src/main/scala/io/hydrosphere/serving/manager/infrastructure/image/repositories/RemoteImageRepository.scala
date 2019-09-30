package io.hydrosphere.serving.manager.infrastructure.image.repositories

import cats.effect.Sync
import com.spotify.docker.client.messages.RegistryAuth
import com.spotify.docker.client.{DockerClient, ProgressHandler}
import io.hydrosphere.serving.manager.config.DockerRepositoryConfiguration
import io.hydrosphere.serving.manager.domain.image.{DockerImage, ImageRepository}
import io.hydrosphere.serving.manager.infrastructure.docker.{DockerClientHelper, DockerRegistryAuth}

class RemoteImageRepository[F[_]: Sync](
  dockerClient: DockerClient,
  conf: DockerRepositoryConfiguration.Remote,
  progressHandler: ProgressHandler)
  extends ImageRepository[F] {

  override def push(dockerImage: DockerImage, progressHandler: ProgressHandler): F[Unit] = Sync[F].delay {
    val auth: RegistryAuth = if (conf.username.isEmpty && conf.password.isEmpty) {
      RegistryAuth.fromDockerConfig(conf.host).build()
    } else {
      DockerClientHelper.createRegistryAuth(DockerRegistryAuth(
        username = conf.username,
        password = conf.password,
        email = None,
        serverAddress = Some(conf.host),
        None,
        None
      ))
    }
    dockerClient.push(dockerImage.fullName, progressHandler, auth)
  }

  override def getImage(name: String, tag: String): DockerImage = {
    DockerImage(
      name = s"${conf.host}/${conf.imagePrefix.getOrElse("")}$name",
      tag = tag
    )
  }
}