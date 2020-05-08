package io.hydrosphere.serving.manager.infrastructure.docker

import java.nio.file.Path
import java.util.concurrent.ConcurrentSkipListSet

import com.spotify.docker.client.DockerClient.RemoveContainerParam
import com.spotify.docker.client.exceptions.DockerException
import com.spotify.docker.client.messages.{ContainerConfig, ContainerCreation}
import com.spotify.docker.client.{DefaultDockerClient, DockerClient, ProgressHandler}

import scala.jdk.CollectionConverters._

object IsolatedDockerClient {
  def createFromEnv = new IsolatedDockerClient(DefaultDockerClient.fromEnv)
}

class IsolatedDockerClient private (val builder: DefaultDockerClient.Builder)
    extends DefaultDockerClient(builder) {
  private val imageStorage     = new ConcurrentSkipListSet[String]
  private val containerStorage = new ConcurrentSkipListSet[String]

  override def build(
      directory: Path,
      name: String,
      dockerfile: String,
      handler: ProgressHandler,
      params: DockerClient.BuildParam*
  ): String = {
    println(s"[ISODOCKER] build: $name dockerfile=$dockerfile")
    val image = super.build(directory, name, dockerfile, handler, params: _*)
    imageStorage.add(image)
    image
  }

  override def removeContainer(containerId: String, params: RemoveContainerParam*): Unit = {
    println(s"[ISODOCKER] removeContainerContainer: $containerId")
    super.removeContainer(containerId, params: _*)
    containerStorage.remove(containerId)
  }

  override def createContainer(config: ContainerConfig, name: String): ContainerCreation = {
    println(s"[ISODOCKER] createContainer: $name image=${config.image()}")
    val creation = super.createContainer(config, name)
    containerStorage.add(creation.id)
    creation
  }

  override def startContainer(containerId: String): Unit = {
    println(s"[ISODOCKER] startContainer: $containerId")
    super.startContainer(containerId)
  }

  override def stopContainer(containerId: String, secondsToWaitBeforeKilling: Int): Unit = {
    println(s"[ISODOCKER] stopContainer: $containerId timeout=$secondsToWaitBeforeKilling")
    super.stopContainer(containerId, secondsToWaitBeforeKilling)
  }

  def clear(): Unit = {
    for (container <- containerStorage.asScala)
      try {
        super.removeContainer(container, RemoveContainerParam.forceKill)
        containerStorage.remove(container)
      } catch {
        case e @ (_: DockerException | _: InterruptedException) =>
          e.printStackTrace()
      }
    for (image <- imageStorage.asScala)
      try {
        super.removeImage(image, true, true)
        imageStorage.remove(image)
      } catch {
        case e @ (_: DockerException | _: InterruptedException) =>
          e.printStackTrace()
      }
  }

  override def close(): Unit = {
    clear()
    super.close()
  }
}
