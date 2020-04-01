package io.hydrosphere.serving.manager.infrastructure

import cats.effect.IO
import com.spotify.docker.client.messages.{ProgressMessage, RegistryAuth}
import com.spotify.docker.client.{DefaultDockerClient, ProgressHandler}
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.infrastructure.docker.DockerdClient

class DockerClientSpec extends GenericUnitTest {
  describe("Image builder") {
    it("should fail") {
      val ph = new ProgressHandler {
        override def progress(message: ProgressMessage): Unit = println(message.toString)
      }
      val dc = DefaultDockerClient.fromEnv().build()
      val client = DockerdClient.create[IO](dc).unsafeRunSync()
      val regAuth = RegistryAuth.builder().build()
      val result = client.push("127.0.0.1/openjdk:alpine:latest", ph, regAuth).attempt.unsafeRunSync()
      assert(result.isLeft, result)
    }
  }
}