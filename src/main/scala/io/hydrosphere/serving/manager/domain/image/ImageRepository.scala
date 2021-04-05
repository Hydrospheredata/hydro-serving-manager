package io.hydrosphere.serving.manager.domain.image

import cats.effect.Sync
import com.spotify.docker.client.{DockerClient, ProgressHandler}
import io.hydrosphere.serving.manager.config.DockerRepositoryConfiguration
import io.hydrosphere.serving.manager.infrastructure.docker.DockerdClient
import io.hydrosphere.serving.manager.infrastructure.image.repositories.{
  ECSImageRepository,
  LocalImageRepository,
  RemoteImageRepository
}

import scala.concurrent.ExecutionContext

trait ImageRepository[F[_]] {
  def push(dockerImage: DockerImage, progressHandler: ProgressHandler): F[Unit]

  def getImage(name: String, tag: String): DockerImage
}

object ImageRepository {
  def fromConfig[F[_]: Sync](
      dockerClient: DockerdClient[F],
      dockerRepositoryConfiguration: DockerRepositoryConfiguration
  )(implicit executionContext: ExecutionContext): ImageRepository[F] =
    dockerRepositoryConfiguration match {
      case c: DockerRepositoryConfiguration.Remote => new RemoteImageRepository[F](dockerClient, c)
      case c: DockerRepositoryConfiguration.Ecs    => new ECSImageRepository[F](dockerClient, c)
      case DockerRepositoryConfiguration.Local     => new LocalImageRepository[F]
    }
}
