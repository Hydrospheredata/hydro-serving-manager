package io.hydrosphere.serving.manager.it.infrastructure

import cats.effect.IO
import com.spotify.docker.client.messages.ContainerConfig
import io.hydrosphere.serving.manager.config.CloudDriverConfiguration
import io.hydrosphere.serving.manager.domain.clouddriver.{CloudInstance, DockerDriver, DockerdClient}
import io.hydrosphere.serving.manager.it.IsolatedDockerAccessIT

import scala.collection.JavaConverters._

class DockerDriverSpec extends IsolatedDockerAccessIT {
  describe("DockerdClinet") {
    it("should correctly map starting containers to CloudInstances") {
      val client = DockerdClient.create[IO](dockerClient)
      val config = CloudDriverConfiguration.Docker("local", None)
      val c = ContainerConfig.builder()
        .image("gan_model:1")
        .labels(Map(
          "HS_INSTANCE_NAME" -> "test",
          "HS_INSTANCE_MV_ID" -> "1"
        ).asJava)
        .attachStdout(true)
        .attachStderr(true)
        .build()
      val r = client.createContainer(c, None).unsafeRunSync()
      val driver = new DockerDriver[IO](client, config)
      val list = driver.instances.unsafeRunSync()
      println(list)
      assert(list.head.status.isInstanceOf[CloudInstance.Status.Starting.type])
    }
    it("should correctly map running containers to CloudInstances") {
      val client = DockerdClient.create[IO](dockerClient)
      val config = CloudDriverConfiguration.Docker("local", None)
      val c = ContainerConfig.builder()
        .image("gan_model:1")
        .labels(Map(
          "HS_INSTANCE_NAME" -> "test",
          "HS_INSTANCE_MV_ID" -> "1"
        ).asJava)
        .attachStdout(true)
        .attachStderr(true)
        .build()
      val r = client.createContainer(c, None).unsafeRunSync()
      client.runContainer(r.id()).unsafeRunSync()
      Thread.sleep(5000)
      val driver = new DockerDriver[IO](client, config)
      val list = driver.instances.unsafeRunSync()
      println(list)
      assert(list.head.status.isInstanceOf[CloudInstance.Status.Running])
    }

    it("should correctly map stopped containers to CloudInstances") {
      val client = DockerdClient.create[IO](dockerClient)
      val config = CloudDriverConfiguration.Docker("local", None)
      val c = ContainerConfig.builder()
        .image("gan_model:1")
        .labels(Map(
          "HS_INSTANCE_NAME" -> "test",
          "HS_INSTANCE_MV_ID" -> "1"
        ).asJava)
        .attachStdout(true)
        .attachStderr(true)
        .build()
      val r = client.createContainer(c, None).unsafeRunSync()
      client.runContainer(r.id()).unsafeRunSync()
      dockerClient.pauseContainer(r.id())
      val driver = new DockerDriver[IO](client, config)
      val list = driver.instances.unsafeRunSync()
      println(list)
      assert(list.head.status.isInstanceOf[CloudInstance.Status.Stopped.type])
    }
  }
}
