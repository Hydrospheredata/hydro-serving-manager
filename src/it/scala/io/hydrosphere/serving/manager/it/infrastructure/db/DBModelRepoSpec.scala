package io.hydrosphere.serving.manager.it.infrastructure.db

import cats.effect.IO
import doobie.scalatest.IOChecker
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBModelRepository
import io.hydrosphere.serving.manager.it.FullIntegrationSpec
import org.scalatest.FunSuite

import scala.concurrent.ExecutionContext

class DBModelRepoSpec extends FunSuite  with IOChecker with FullIntegrationSpec {
  implicit val cs = IO.contextShift(ExecutionContext.global)

  test("Queries") {
    check(DBModelRepository.allQ)
  }

}
