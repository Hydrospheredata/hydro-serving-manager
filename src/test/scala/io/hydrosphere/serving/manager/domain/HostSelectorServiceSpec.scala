package io.hydrosphere.serving.manager.domain

import cats.Id
import cats.implicits._
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.host_selector.{
  HostSelector,
  HostSelectorRepository,
  HostSelectorService
}

class HostSelectorServiceSpec extends GenericUnitTest {
  describe("Environment management service") {
    it("should return an environment by id") {
      val envRepo = mock[HostSelectorRepository[Id]]

      when(envRepo.get("test")).thenReturn(
        HostSelector(1, "test", Map("foo" -> "bar")).some
      )

      val environmentService = HostSelectorService(envRepo)

      val res = environmentService.get("test")
      assert(res.isRight, res)
      val env = res.getOrElse(fail())
      assert(env.name == "test")
      assert(env.id == 1)
      assert(env.nodeSelector == Map("foo" -> "bar"))
    }

    it("should create a new environment") {
      val envRepo = mock[HostSelectorRepository[Id]]

      when(envRepo.get("new_test")).thenReturn(None)
      val hostSelector = HostSelector(
        1,
        "new_test",
        Map("foo" -> "bar")
      )
      when(envRepo.create(any)).thenReturn(hostSelector)

      val environmentService = HostSelectorService(envRepo)

      val res = environmentService.create("new_test", Map("foo" -> "bar"))
      assert(res.isRight, res)
      val env = res.getOrElse(fail())
      assert(env.name == "new_test")
      assert(env.nodeSelector == Map("foo" -> "bar"))

    }

    it("should reject a creation of duplicate environments") {
      val envRepo = mock[HostSelectorRepository[Id]]

      when(envRepo.get("new_test"))
        .thenReturn(HostSelector(1, "new_test", Map("foo" -> "bar")).some)

      val environmentService = HostSelectorService(envRepo)
      val res                = environmentService.create("new_test", Map("foo" -> "bar"))
      assert(res.isLeft, res)
    }
  }
}
