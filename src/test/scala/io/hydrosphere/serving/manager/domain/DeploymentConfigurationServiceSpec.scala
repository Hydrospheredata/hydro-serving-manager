package io.hydrosphere.serving.manager.domain

import cats.Id
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.deploy_config.{DeploymentConfiguration, HostSelectorRepository, HostSelectorService}
import org.mockito.{Matchers, Mockito}

class DeploymentConfigurationServiceSpec extends GenericUnitTest {
  describe("Environment management service") {
    it("should return an environment by id") {
      val envRepo = mock[HostSelectorRepository[Id]]

      Mockito.when(envRepo.get("test")).thenReturn(
        Some(
          DeployConfiguration(1, "test", Map("foo" -> "bar"))
        )
      )

      val environmentService = HostSelectorService(envRepo)

      val res = environmentService.get("test")
      assert(res.isRight, res)
      val env = res.right.get
      assert(env.name === "test")
      assert(env.id === 1)
      assert(env.nodeSelector === Map("foo" -> "bar"))
    }

    it("should create a new environment") {
      val envRepo = mock[HostSelectorRepository[Id]]

      Mockito.when(envRepo.get("new_test")).thenReturn(
        None
      )
      val hostSelector = DeployConfiguration(
        1,
        "new_test",
        Map("foo" -> "bar")
      )
      when(envRepo.create(Matchers.any())).thenReturn(hostSelector)

      val environmentService = HostSelectorService(envRepo)

      val res = environmentService.create("new_test", Map("foo" -> "bar"))
      assert(res.isRight, res)
      val env = res.right.get
      assert(env.name === "new_test")
      assert(env.nodeSelector === Map("foo" -> "bar"))

    }

    it("should reject a creation of duplicate environments") {
      val envRepo = mock[HostSelectorRepository[Id]]

      Mockito.when(envRepo.get("new_test")).thenReturn(
        Some(DeployConfiguration(1, "new_test", Map("foo" -> "bar")))
      )

      val environmentService = HostSelectorService(envRepo)
      val res = environmentService.create("new_test", Map("foo" -> "bar"))
      assert(res.isLeft, res)
    }
  }
}
