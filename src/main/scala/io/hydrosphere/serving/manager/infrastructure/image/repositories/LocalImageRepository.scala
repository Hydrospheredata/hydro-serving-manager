package io.hydrosphere.serving.manager.infrastructure.image.repositories

import cats.Applicative
import com.spotify.docker.client.ProgressHandler
import io.hydrosphere.serving.manager.domain.image.{DockerImage, ImageRepository}

class LocalImageRepository[F[_]: Applicative] extends ImageRepository[F] {
  override def getImageForModelVersion(name: String, tag: String): DockerImage = {
    DockerImage(
      name = name,
      tag = DockerImage.tag(tag)
    )
  }

  override def push(dockerImage: DockerImage, progressHandler: ProgressHandler): F[Unit] = {
    Applicative[F].pure(())
  }
}