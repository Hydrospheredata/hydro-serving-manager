package io.hydrosphere.serving.manager.it.infrastructure.db

import cats.effect.IO
import doobie.scalatest.IOChecker
import io.hydrosphere.serving.manager.domain.model.Model
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
    it("should get insert a model") {
      val q = for {
        res <- app.core.repos.modelRepo.create(Model(0, "a-model"))
      } yield {
        assert(res.id == 1)
      }
      q.unsafeToFuture()
    }
    it("should get a model") {
      val q = for {
        resId <- app.core.repos.modelRepo.get(1)
        resName <- app.core.repos.modelRepo.get("a-model")
      } yield {
        assert(resId.isDefined)
        assert(resId == resName)
      }
      q.unsafeToFuture()
    }
    it("should get all models") {
      val q = for {
        res <- app.core.repos.modelRepo.all()
      } yield {
        assert(res.size == 1)
      }
      q.unsafeToFuture()
    }
    it("should delete a model") {
      val q = for {
        changed <- app.core.repos.modelRepo.delete(1)
        empty <- app.core.repos.modelRepo.get(1)
      } yield {
        assert(changed == 1)
        assert(empty.isEmpty, empty)
      }
      q.unsafeToFuture()
    }
  }
}
