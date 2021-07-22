package io.hydrosphere.serving.manager.infrastructure

import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.image.DockerImage
import cats.implicits._

class DockerImageSpec extends GenericUnitTest {
  describe("check parse") {
    it("should parse correctly") {
      val di = DockerImage("image", "latest")
      val r  = di.replaceHost("docker.io/pmakarichev")
      assert(r.map(_.toString) == "docker.io/pmakarichev/image:latest".asRight, "huita")
    }
  }

}
