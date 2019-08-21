package io.hydrosphere.serving.manager.it.infrastructure.db

import cats.effect.IO
import doobie.scalatest.IOChecker
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBModelRepository
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBModelRepository.ModelRow
import io.hydrosphere.serving.manager.it.FullIntegrationSpec
import org.scalatest.FunSuite

import scala.concurrent.ExecutionContext

class DBModelRepoSpec extends FullIntegrationSpec with IOChecker {
  override def transactor: doobie.Transactor[IO] = app.transactor
  describe("Queries") {
    it("should have correct queries") {
      val row = ModelRow(1, "test")
      check(DBModelRepository.allQ)
      check(DBModelRepository.deleteQ(1))
      check(DBModelRepository.createQ(row))
      check(DBModelRepository.updateQ(row))
      check(DBModelRepository.getByIdQ(1))
      check(DBModelRepository.getByNameQ("test"))
      succeed
    }
  }

  describe("Methods") {
  }
}
