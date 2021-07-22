package io.hydrosphere.serving.manager.domain

import cats.implicits._
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.image.DockerImage

class DockerImageSpec extends GenericUnitTest {
  describe("host replacing") {
    it("with common host parsed correctly") {
      val dockerImage = DockerImage("registry/adult_scalar", "latest")
      val result      = dockerImage.replaceHost("registry")

      assert(result.map(_.fullName) == "registry/adult_scalar:latest".asRight)
    }

    it("with host with suffix parsed correctly") {
      val dockerImage = DockerImage("docker.io/user/adult_scalar", "latest")
      val result      = dockerImage.replaceHost("docker.io/user")

      assert(result.map(_.fullName) == "docker.io/user/adult_scalar:latest".asRight)
    }
  }
}
