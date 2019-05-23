package io.hydrosphere.serving.manager.domain

import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.servable.ServableMapper

class ServableSpec extends GenericUnitTest {
  describe("ServableSpec") {
    it("should restore correct name") {
      val fullName = "claims-1-awesome-afternoon"
      val modelName = "claims"
      val modelVersion = 1
      val result = ServableMapper.deconstructName(fullName, modelName, modelVersion)
      assert(result.get === "awesome-afternoon")
    }
    it("should fail on invalid name") {
      val fullName = "mnist-1-good-afternoon"
      val modelName = "claim"
      val modelVersion = 1
      val result = ServableMapper.deconstructName(fullName, modelName, modelVersion)
      assert(result.isEmpty, result)
    }
  }
}