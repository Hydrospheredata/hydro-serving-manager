package io.hydrosphere.serving.manager.it.infrastructure.db

import java.time.Instant

import cats.data.NonEmptyList
import doobie.scalatest.IOChecker
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.contract.model_field.ModelField
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.api.http.controller.model.ModelUploadMetadata
import io.hydrosphere.serving.manager.data_profile_types.DataProfileType
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionStatus}
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBServableRepository
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBServableRepository.ServableRow
import io.hydrosphere.serving.manager.it.FullIntegrationSpec
import io.hydrosphere.serving.tensorflow.types.DataType.DT_DOUBLE


class DBServableRepoSpec extends FullIntegrationSpec with IOChecker {
  val transactor = app.transactor

  var mv1: ModelVersion.Internal = _

  describe("Queries") {
    it("should have correct queries") {
      val row = ServableRow("name", 123, "status_text", Some("host"), Some(123), "status", None, None)
      check(DBServableRepository.allQ)
      check(DBServableRepository.getManyQ(NonEmptyList.of("123", "test")))
      check(DBServableRepository.deleteQ("delete-me"))
      check(DBServableRepository.getQ("get-me"))
      check(DBServableRepository.upsertQ(row))
      check(DBServableRepository.findForModelVersionQ(1))
      succeed
    }
  }
  describe("Methods") {
    it("should upsert new Servable") {
      val servable = Servable(mv1, "test-servable", Servable.Serving("Ok", "localhost", 9090), Nil)
      val result = app.core.repos.servableRepo.upsert(servable).unsafeRunSync()
      println(result)
      assert(result.fullName === "model-name-1-test-servable")
    }
    it("should get Servable by name") {
      val res = app.core.repos.servableRepo.get("model-name-1-test-servable").unsafeRunSync()
      println(res)
      assert(res.isDefined, res)
      assert(res.get.modelVersion === mv1)
      assert(res.get.nameSuffix === "test-servable")
    }
    it("should read names correctly") {
      assert(Servable.extractSuffix("claims_model", 1, "claims-model-1-far-moon") == "far-moon")
    }
    it("should get many servables") {
      val res = app.core.repos.servableRepo.get("model-name-1-test-servable" :: "kek" :: Nil).unsafeRunSync()
      println(res)
      assert(res.size == 1)
      assert(res.head.modelVersion === mv1)
      assert(res.head.nameSuffix === "test-servable")
    }
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    val f = for {
      m <- app.core.repos.modelRepo.create(Model(1, "model-name"))
      mv = ModelVersion.Internal(1, DockerImage("qwe", "asdasd"), Instant.now(), Some(Instant.now()), 1, ModelContract.defaultInstance, dummyImage, m, ModelVersionStatus.Released, None, Map.empty)
      mv <- app.core.repos.versionRepo.create(mv)
    } yield {
      println(s"Created: $mv")
      mv1 = mv.asInstanceOf[ModelVersion.Internal]
    }
    f.unsafeRunSync()
  }
}
