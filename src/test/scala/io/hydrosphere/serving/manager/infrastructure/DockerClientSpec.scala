package io.hydrosphere.serving.manager.infrastructure

import cats.effect.IO
import cats.implicits._
import com.spotify.docker.client.DockerClient.RemoveContainerParam
import com.spotify.docker.client.messages.{ContainerConfig, ProgressMessage, RegistryAuth}
import com.spotify.docker.client.{DefaultDockerClient, ProgressHandler}
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.infrastructure.docker.DockerdClient

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class DockerClientSpec extends GenericUnitTest {
  describe("Image builder") {
    it("should fail") {
      val ph = new ProgressHandler {
        override def progress(message: ProgressMessage): Unit = println(message.toString)
      }
      val dc      = DefaultDockerClient.fromEnv().build()
      val client  = DockerdClient.create[IO](dc).unsafeRunSync()
      val regAuth = RegistryAuth.builder().build()
      val result =
        client.push("127.0.0.1/openjdk:alpine:latest", ph, regAuth).attempt.unsafeRunSync()
      assert(result.isLeft, result)
    }
  }

  describe("DockerClient") {
    ignore("should handle logs correctly") {
      val containerName = "keke"

      val client = DockerdClient.fromEnv[IO].unsafeRunSync()
      val conf = ContainerConfig
        .builder()
        .image("busybox")
        .cmd("/bin/sh", "-c", "while true; do date; sleep 1; done")
        .build()
      client.createContainer(conf, containerName.some).unsafeRunSync()
      client.runContainer(containerName).unsafeRunSync()
      val logs = client.logs(containerName, follow = true)
      println("logs")
      // println(logs.readFully())
      val fiber = logs.evalTap(x => IO(print(x))).compile.drain.start.unsafeRunSync()
      (IO.sleep(4.seconds) >> fiber.cancel >> client.removeContainer(
        containerName,
        List(RemoveContainerParam.forceKill())
      )).unsafeRunSync()
      succeed
    }
  }
}
