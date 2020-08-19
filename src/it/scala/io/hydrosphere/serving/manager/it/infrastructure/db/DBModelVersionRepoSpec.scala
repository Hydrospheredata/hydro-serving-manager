package io.hydrosphere.serving.manager.it.infrastructure.db

import java.time.Instant

import cats.data.OptionT
import cats.effect.IO
import doobie.scalatest.IOChecker
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionStatus}
import io.hydrosphere.serving.manager.domain.monitoring.MonitoringConfiguration
import spray.json._
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBModelVersionRepository
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBModelVersionRepository.ModelVersionRow
import io.hydrosphere.serving.manager.it.FullIntegrationSpec


class DBModelVersionRepoSpec extends FullIntegrationSpec with IOChecker {
  val transactor = app.transactor
  val time = Instant.now()
  var model: Model = _
  var version: ModelVersion.Internal = _

  describe("Queries") {
    val row = ModelVersionRow(
      model_version_id = 1,
      model_id = 1,
      created_timestamp = time,
      finished_timestamp = Some(time),
      model_version = 1337,
      model_contract = "contract",
      image_name = dummyImage.name,
      image_tag = dummyImage.tag,
      image_sha256 = dummyImage.sha256,
      runtime_name = dummyImage.name,
      runtime_version = dummyImage.tag,
      status = "status",
      profile_types = None,
      install_command = Some("echo 123"),
      metadata = Some("{}"),
      is_external = false,
      monitoring_configuration = MonitoringConfiguration().toJson
    )
    it("should have valid queries") {
      check(DBModelVersionRepository.allQ)
      check(DBModelVersionRepository.getQ(1))
      check(DBModelVersionRepository.getQ("model", 12))
      check(DBModelVersionRepository.insertQ(row))
      check(DBModelVersionRepository.updateQ(row))
      check(DBModelVersionRepository.deleteQ(1))
      succeed
    }
  }
  describe("Methods") {
    it("should insert a version") {
      val q = for {
        result <- app.core.repos.versionRepo.create(version)
      } yield {
        assert(result.id == 1)
      }
      q.unsafeToFuture()
    }

    it("should insert a external version") {
      val ev = ModelVersion.External(0, Instant.now(), 1337, ModelContract.defaultInstance, model, Map.empty)
      val q = for {
        result <- app.core.repos.versionRepo.create(ev)
        got <- app.core.repos.versionRepo.get(result.id)
      } yield {
        assert(got.isDefined)
        assert(got.get.isInstanceOf[ModelVersion.External])
      }
      q.unsafeToFuture()
    }

    it("should update a version") {
      val q = for {
        existing <- OptionT(app.core.repos.versionRepo.get(1)).getOrElseF(IO.raiseError(new RuntimeException("Version not found")))
        updatedEx = existing match {
          case x: ModelVersion.Internal => x.copy(status = ModelVersionStatus.Assembling)
          case x: ModelVersion.External => fail("Unexpected External model")
        }
        changed <- app.core.repos.versionRepo.update(updatedEx)
        result <- OptionT(app.core.repos.versionRepo.get(1)).getOrElseF(IO.raiseError(new RuntimeException("Version not found")))
      } yield {
        assert(changed == 1)
        assert(result.id == 1)
        assert(result.asInstanceOf[ModelVersion.Internal].status == ModelVersionStatus.Assembling)
      }
      q.unsafeToFuture()
    }
    it("should fetch all versions") {
      val q = for {
        all <- app.core.repos.versionRepo.all()
      } yield {
        assert(all.size == 2)
      }
      q.unsafeToFuture()
    }
    it("should get a version") {
      val q = for {
        result <- OptionT(app.core.repos.versionRepo.get("model-name", 1))
          .getOrElseF(IO.raiseError(new RuntimeException("Version not found")))
      } yield {
        assert(result.id == 1)
      }
      q.unsafeToFuture()
    }
    it("should get many versions") {
      val q = for {
        result <- OptionT(app.core.repos.versionRepo.get("model-name", 1))
          .getOrElseF(IO.raiseError(new RuntimeException("Version not found")))
      } yield {
        assert(result.id == 1)
      }
      q.unsafeToFuture()
    }
    it("should delete a version") {
      val q = for {
        added <- app.core.repos.versionRepo.create(version.copy(modelVersion = 2))
        result <- OptionT(app.core.repos.versionRepo.get("model-name", 2))
          .getOrElseF(IO.raiseError(new RuntimeException("Version not found")))
        changed <- app.core.repos.versionRepo.delete(result.id)
        empty <- app.core.repos.versionRepo.get("model-name", 2)
      } yield {
        assert(changed == 1)
        assert(empty.isEmpty, empty)
      }
      q.unsafeToFuture()
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    val f = for {
      m <- app.core.repos.modelRepo.create(Model(1, "model-name"))
    } yield {
      println(s"Created: $m")
      model = m
      version = ModelVersion.Internal(
        id = 0,
        image = dummyImage,
        created = time,
        finished = Some(time),
        modelVersion = 1,
        modelContract = ModelContract.defaultInstance,
        runtime = dummyImage,
        model = model,
        status = ModelVersionStatus.Released,
        installCommand = Some("echo 123"),
        metadata = Map("author" -> "me")
      )
    }
    f.unsafeRunSync()
  }
}
