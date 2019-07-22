package io.hydrosphere.serving.manager.infrastructure.db

import cats.effect.IO
import doobie.scalatest.IOChecker
import io.hydrosphere.serving.manager.H2Support
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBModelRepository
import org.scalatest.FunSuite

import scala.concurrent.ExecutionContext

class DBModelRepoSpec extends FunSuite with H2Support with IOChecker {
  implicit val cs = IO.contextShift(ExecutionContext.global)

  test("Queries") {
    check(DBModelRepository.allQ)
  }

  override def transactor: doobie.Transactor[IO] = makeH2Transactor[IO]().allocated.unsafeRunSync()._1
}
