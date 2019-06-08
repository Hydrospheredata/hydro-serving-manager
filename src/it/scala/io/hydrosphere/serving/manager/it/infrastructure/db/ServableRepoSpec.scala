package io.hydrosphere.serving.manager.it.infrastructure.db

import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.contract.model_field.ModelField
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.api.http.controller.model.ModelUploadMetadata
import io.hydrosphere.serving.manager.data_profile_types.DataProfileType
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.hydrosphere.serving.manager.it.FullIntegrationSpec
import io.hydrosphere.serving.tensorflow.types.DataType.DT_DOUBLE

class ServableRepoSpec extends FullIntegrationSpec {
  private val uploadFile = packModel("/models/dummy_model")
  private val signature = ModelSignature(
    signatureName = "not-default-spark",
    inputs = List(ModelField("test-input", None, DataProfileType.NONE, ModelField.TypeOrSubfields.Dtype(DT_DOUBLE))),
    outputs = List(ModelField("test-output", None, DataProfileType.NONE, ModelField.TypeOrSubfields.Dtype(DT_DOUBLE)))
  )
  private val upload1 = ModelUploadMetadata(
    name = "m1",
    runtime = dummyImage,
    contract = Some(ModelContract(
      predict = Some(signature)
    ))
  )
  var mv1: ModelVersion = _

  describe("DBServableRepository") {
    it("should upsert new Servable") {
      val servable = Servable(mv1, "test-servable", Servable.Serving("Ok", "localhost", 9090))
      val result = repositories.servableRepository.upsert(servable).unsafeRunSync()
      println(result)
      assert(result.fullName === "m1-1-test-servable")
    }
    it("should get Servable by name") {
      val res = repositories.servableRepository.get("m1-1-test-servable").unsafeRunSync()
      println(res)
      assert(res.isDefined, res)
      assert(res.get.modelVersion === mv1)
      assert(res.get.nameSuffix === "test-servable")
    }
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    val f = for {
      d1 <- managerServices.modelService.uploadModel(uploadFile, upload1)
      completed1 <- d1.completed.get
    } yield {
      println(s"UPLOADED: $completed1")
      mv1 = completed1
    }
    f.unsafeRunSync()
  }
}
