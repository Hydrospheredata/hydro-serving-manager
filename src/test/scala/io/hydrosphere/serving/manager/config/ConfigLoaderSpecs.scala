package io.hydrosphere.serving.manager.config

import cats.effect.IO
import cats.implicits._
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.deploy_config.DEFAULT_DEPLOYMENT_CONFIG_NAME
import pureconfig.ConfigSource

class ConfigLoaderSpecs extends GenericUnitTest {
  private val source = ConfigSource.file("src/test/resources/depConf.conf")
  describe("Config loader") {
    it("should load all configs from resources") {
      val config = ManagerConfiguration
        .load[IO](source)
        .unsafeRunSync()
      print(config.show)
      assert(config.defaultDeploymentConfiguration.name == DEFAULT_DEPLOYMENT_CONFIG_NAME)
    }
  }
}
