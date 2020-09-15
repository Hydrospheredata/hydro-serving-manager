package io.hydrosphere.serving.manager.infrastructure.image.repositories

import java.util.Collections

import cats.effect.Sync
import cats.implicits._
import com.amazonaws.services.ecr.model._
import com.amazonaws.services.ecr.{AmazonECR, AmazonECRClientBuilder}
import com.spotify.docker.client.ProgressHandler
import io.hydrosphere.serving.manager.config.DockerRepositoryConfiguration
import io.hydrosphere.serving.manager.domain.image.{DockerImage, ImageRepository}
import io.hydrosphere.serving.manager.infrastructure.docker.{DockerRegistryAuth, DockerdClient}

class ECSImageRepository[F[_]: Sync](
  dockerClient: DockerdClient[F],
  ecsDockerRepositoryConfiguration: DockerRepositoryConfiguration.Ecs
) extends ImageRepository[F] {

  val ecrClient: AmazonECR = AmazonECRClientBuilder.standard()
    .withRegion(ecsDockerRepositoryConfiguration.region)
    .build()

  override def push(dockerImage: DockerImage, progressHandler: ProgressHandler): F[Unit] = {
    for {
      _ <- createRepositoryIfNeeded(dockerImage.name)
      auth <- getDockerRegistryAuth
      res <- dockerClient.push(
        dockerImage.fullName,
        progressHandler,
        auth.underlying
      )
    } yield res
  }

  override def getImage(modelName: String, modelVersion: String): DockerImage = {
    DockerImage(
      name = s"${ecsDockerRepositoryConfiguration.accountId}.dkr.ecr.${ecsDockerRepositoryConfiguration.region.getName}.amazonaws.com/$modelName",
      tag = modelVersion.toString
    )
  }

  private def getDockerRegistryAuth: F[DockerRegistryAuth] = Sync[F].delay {
    val getAuthorizationTokenRequest = new GetAuthorizationTokenRequest
    getAuthorizationTokenRequest.setRegistryIds(Collections.singletonList(ecsDockerRepositoryConfiguration.accountId))
    val result = ecrClient.getAuthorizationToken(getAuthorizationTokenRequest)

    val authorizationData = result.getAuthorizationData.get(0)

    DockerRegistryAuth(
      username = None,
      password = None,
      email = None,
      identityToken = None,
      serverAddress = Some(authorizationData.getProxyEndpoint),
      auth = Some(authorizationData.getAuthorizationToken)
    )
  }

  private def createRepositoryIfNeeded(modelName: String): F[Unit] = Sync[F].delay {
    val req = new DescribeRepositoriesRequest
    req.setRepositoryNames(Collections.singletonList(modelName))
    req.setRegistryId(ecsDockerRepositoryConfiguration.accountId)
    try {
      ecrClient.describeRepositories(req)
    } catch {
      case _: RepositoryNotFoundException =>
        val createRepositoryRequest = new CreateRepositoryRequest
        createRepositoryRequest.setRepositoryName(modelName)
        ecrClient.createRepository(createRepositoryRequest)
    }
  }

}