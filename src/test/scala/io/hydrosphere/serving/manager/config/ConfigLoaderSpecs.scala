package io.hydrosphere.serving.manager.config

import cats.effect.IO
import io.hydrosphere.serving.manager.GenericUnitTest
import pureconfig.ConfigSource

class ConfigLoaderSpecs extends GenericUnitTest {
  private val source = ConfigSource.file("src/test/resources/depConf.conf")
  describe("Config loader") {
    it("should load all configs from resources") {
      val config = ManagerConfiguration
        .load[IO](source)
        .unsafeRunSync()
      print(config)
      assert(config.defaultDeploymentConfiguration.isDefined)
      assert(config.defaultDeploymentConfiguration.get.name == "default")
    }
  }
}
