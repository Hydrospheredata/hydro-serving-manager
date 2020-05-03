package io.hydrosphere.serving.manager.infrastructure.docker

import cats.effect.Sync
import cats.implicits._
import com.spotify.docker.client.ProgressHandler
import com.spotify.docker.client.messages.RegistryAuth
import io.hydrosphere.serving.manager.config.DockerRepositoryConfiguration
import io.hydrosphere.serving.manager.domain.image.{DockerImage, ImageRepository}

class RemoteImageRepository[F[_]: Sync](
    dockerClient: DockerdClient[F],
    conf: DockerRepositoryConfiguration.Remote
) extends ImageRepository[F] {

  override def push(dockerImage: DockerImage, progressHandler: ProgressHandler): F[Unit] = {
    for {
      auth <- if (conf.username.isEmpty && conf.password.isEmpty) {
        Sync[F].delay(RegistryAuth.fromDockerConfig(conf.host).build())
      } else {
        Sync[F].pure(
          DockerRegistryAuth(
            username = conf.username,
            password = conf.password,
            email = None,
            serverAddress = Some(conf.host),
            None,
            None
          ).inderlying
        )
      }
      res <- dockerClient.push(dockerImage.fullName, progressHandler, auth)
    } yield res
  }

  override def getImageForModelVersion(name: String, tag: String): DockerImage = {
    DockerImage(
      user = Some(conf.host),
      name = s"${conf.imagePrefix.getOrElse("")}$name",
      tag = tag
    )
  }
}
