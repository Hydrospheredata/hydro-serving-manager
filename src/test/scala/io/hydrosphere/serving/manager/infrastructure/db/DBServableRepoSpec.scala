package io.hydrosphere.serving.manager.infrastructure.db

import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.servable.Servable

class DBServableRepoSpec extends GenericUnitTest {
  describe("DBServableRepo") {
    it("should read names correctly") {
      assert(Servable.extractSuffix("claims_model", 1, "claims-model-1-far-moon") == "far-moon")
    }
  }
}
