package io.hydrosphere.serving.manager.infrastructure.docker

import java.nio.ByteBuffer

import cats.effect.Sync
import cats.implicits._
import com.spotify.docker.client.DockerClient.{ListContainersParam, RemoveContainerParam}
import com.spotify.docker.client.messages.{Container, ContainerConfig, ContainerCreation}
import com.spotify.docker.client.{DefaultDockerClient, DockerClient, LogStream}

import scala.collection.JavaConverters._

trait DockerdClient[F[_]]{
  
  def createContainer(container: ContainerConfig, name: Option[String]): F[ContainerCreation]
  
  def runContainer(id: String): F[Unit]
  
  def removeContainer(id: String, params: List[RemoveContainerParam]): F[Unit]
  def removeContainer(id: String): F[Unit] = removeContainer(id, Nil)
  
  def listContainers(params: List[ListContainersParam]): F[List[Container]]
  def listRunningContainers: F[List[Container]] = listContainers(Nil)
  def listAllContainers: F[List[Container]] = listContainers(ListContainersParam.allContainers() :: Nil)
  
  def logs(id: String, follow: Boolean): F[LogStream]
}

object DockerdClient {
  
  def create[F[_]](implicit F: Sync[F]): DockerdClient[F] =
    DockerdClient.create(DefaultDockerClient.fromEnv().build())
  
  def create[F[_]](underlying: DockerClient)(implicit F: Sync[F]): DockerdClient[F] =
    new DockerdClient[F] {
      
      override def createContainer(container: ContainerConfig, name: Option[String]): F[ContainerCreation] = {
        F.delay {
          name match {
            case Some(n) => underlying.createContainer(container, n)
            case None => underlying.createContainer(container)
          }
        }
      }
      
      override def runContainer(id: String): F[Unit] =
        F.delay(underlying.startContainer(id))
  
      override def removeContainer(id: String, params: List[RemoveContainerParam]): F[Unit] = {
        F.delay(underlying.removeContainer(id, params: _*))
      }
  
      override def listContainers(params: List[ListContainersParam]): F[List[Container]] = {
        F.delay(underlying.listContainers(params: _*)).map(_.asScala.toList)
      }

      override def logs(id: String, follow: Boolean): F[LogStream] = {
        if (follow) {
          F.delay(underlying.logs(id, DockerClient.LogsParam.stderr(), DockerClient.LogsParam.stdout(), DockerClient.LogsParam.follow()))
        } else {
          F.delay(underlying.logs(id, DockerClient.LogsParam.stderr(), DockerClient.LogsParam.stdout()))
        }
      }
    }
  
}
