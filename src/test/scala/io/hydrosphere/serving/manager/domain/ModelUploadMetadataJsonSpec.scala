package io.hydrosphere.serving.manager.domain

import io.hydrosphere.serving.manager.GenericUnitTest
import io.circe.parser._
import io.hydrosphere.serving.manager.api.http.controller.model.ModelUploadMetadata

class ModelUploadMetadataJsonSpec extends GenericUnitTest {
  describe("ModelUploadMetadata") {
    it("should be converted correctly") {
      val rawJson =
        """{
          |"name": "infer",
          |"runtime": {"name": "hydrosphere/serving-runtime-python-3.6", "tag": "2.4.0", "sha256": null},
          |"signature": {
          |"signatureName": "infer",
          |"inputs": [{"name": "input", "profile": "NUMERICAL", "shape": {"dims": []}, "dtype": "DT_INT64"}],
          |"outputs": [{"name": "output", "profile": "NUMERICAL", "shape": {"dims": []}, "dtype": "DT_INT64"}]
          |},
          |"installCommand": null,
          |"metadata": null,
          |"monitoringConfiguration": {"batchSize": 10}}""".stripMargin

      assert(decode[ModelUploadMetadata](rawJson).isRight)
    }
  }
}
