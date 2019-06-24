package io.hydrosphere.serving.manager.domain.image

import java.nio.file.Path

import com.spotify.docker.client.ProgressHandler

trait ImageBuilder[F[_]] {
  def build(
    buildPath: Path,
    image: DockerImage,
    progressHandler: ProgressHandler
  ): F[String]
}
