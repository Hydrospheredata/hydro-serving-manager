package io.hydrosphere.serving.manager.it.infrastructure.db

import cats.data.NonEmptyList
import cats.syntax.option._
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.contract.model_field.ModelField
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.api.http.controller.model.ModelUploadMetadata
import io.hydrosphere.serving.manager.data_profile_types.DataProfileType
import io.hydrosphere.serving.manager.domain.application.Application
import io.hydrosphere.serving.manager.domain.application.graph.{ExecutionNode, Variant}
import io.hydrosphere.serving.manager.domain.application.graph.VersionGraphComposer.PipelineStage
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.hydrosphere.serving.manager.it.FullIntegrationSpec
import io.hydrosphere.serving.tensorflow.types.DataType.DT_DOUBLE

class DBApplicationRepoSpec extends FullIntegrationSpec {
  private val uploadFile = packModel("/models/dummy_model")
  private val signature = ModelSignature(
    signatureName = "not-default-spark",
    inputs = List(ModelField("test-input", None, DataProfileType.NONE, ModelField.TypeOrSubfields.Dtype(DT_DOUBLE))),
    outputs = List(ModelField("test-output", None, DataProfileType.NONE, ModelField.TypeOrSubfields.Dtype(DT_DOUBLE)))
  )
  private val upload1 = ModelUploadMetadata(
    name = "m1",
    runtime = dummyImage,
    contract = ModelContract(
      predict = signature.some
    ).some
  )
  var mv1: ModelVersion = _
  var servable: Servable.OkServable = _

  describe("DbApplicationRepository") {
    it("should create") {
      val application = Application(
        id = 0,
        name = "repo-spec-app",
        namespace = None,
        status = Application.Ready(
          NonEmptyList.of(
            ExecutionNode(
              NonEmptyList.of(Variant(servable, 100)),
              ModelSignature.defaultInstance
            )
          )
        ),
        signature = ModelSignature.defaultInstance,
        kafkaStreaming = List.empty,
        versionGraph = NonEmptyList.of(PipelineStage(NonEmptyList.of(Variant(mv1, 100)), ModelSignature.defaultInstance))
      )
      val result = app.core.repos.appRepo.create(application).unsafeRunSync()
      println(result)
      assert(result.id !== 0)
    }
    it("should retrieve an application by id") {
      val result = app.core.repos.appRepo.get(1).unsafeRunSync().get
      println(result)
      assert(result.name === "repo-spec-app")
    }
    it("should retrieve an application by name") {
      val result = app.core.repos.appRepo.get("repo-spec-app").unsafeRunSync().get
      println(result)
      assert(result.id === 1)
    }
    it("should find app usages") {
      val oldApp = app.core.repos.appRepo.get("repo-spec-app").unsafeRunSync().get
      val result = app.core.repos.appRepo.findServableUsage(servable.fullName).unsafeRunSync()
      assert(result.head.name == oldApp.name)

      val failResult = app.core.repos.appRepo.findServableUsage("hackermans").unsafeRunSync()
      assert(failResult.isEmpty, failResult)
    }
  }
  override protected def beforeAll(): Unit = {
    super.beforeAll()

    val f = for {
      d1 <- app.core.modelService.uploadModel(uploadFile, upload1)
      completed1 <- d1.completed.get
      s = Servable(completed1, "test-suffix", Servable.Serving("ok", "localhost", 9090), Nil)
      _ <- app.core.repos.servableRepo.upsert(s)
    } yield {
      println(s"UPLOADED: $completed1")
      mv1 = completed1
      servable = s
    }
    f.unsafeRunSync()
  }
}
