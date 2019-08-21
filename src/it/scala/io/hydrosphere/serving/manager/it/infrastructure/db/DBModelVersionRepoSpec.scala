package io.hydrosphere.serving.manager.it.infrastructure.db

import java.time.Instant

import doobie.scalatest.IOChecker
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBModelVersionRepository
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBModelVersionRepository.ModelVersionRow
import io.hydrosphere.serving.manager.it.FullIntegrationSpec

class DBModelVersionRepoSpec extends FullIntegrationSpec with IOChecker {
  val transactor = app.transactor
  describe("Queries") {
    it("should have valid queries") {
      val row = ModelVersionRow(
        model_version_id = 1,
        model_id = 1,
        host_selector = Some(2),
        created_timestamp = Instant.now(),
        finished_timestamp = Some(Instant.now()),
        model_version = 1337,
        model_contract = "contract",
        image_name = "image",
        image_tag = "tag",
        image_sha256 = Some("sha"),
        runtime_name = "runtime",
        runtime_version = "runtime version",
        status = "status",
        profile_types = None,
        install_command = Some("echo 123"),
        metadata = Some("{}")
      )
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
    pending
  }
}
