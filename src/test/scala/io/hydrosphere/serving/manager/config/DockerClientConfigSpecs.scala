package io.hydrosphere.serving.manager.config

import java.nio.file.Paths

import cats.effect.IO
import io.hydrosphere.serving.manager.GenericUnitTest

class DockerClientConfigSpecs extends GenericUnitTest {
  describe("DockerClient config parser") {
    it("should load config file") {
      val config = DockerClientConfig.load[IO](getTestResourcePath("docker_configs/proxy_config.json")).unsafeRunSync()
      val default = config.proxies("default")
      assert(default.httpProxy.get === "http://localhost")
      assert(default.httpsProxy.get === "https://localhost")
      assert(default.noProxy.get === "noProxy")
      assert(default.ftpProxy.get === "ftpProxy")
    }

    it("should fall back to default if no file") {
      ioAssert {
        for {
          maybeConfig <- DockerClientConfig.load[IO](Paths.get("docker_configs/qweqwe.json")).attempt
        } yield assert(maybeConfig.isLeft, maybeConfig)
      }
    }
  }
}