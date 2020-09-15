package io.hydrosphere.serving.manager.it.service

import java.nio.file.{Files, Paths}

import doobie.implicits._
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBApplicationRepository
import io.hydrosphere.serving.manager.it.FullIntegrationSpec

import scala.sys.process._


// TODO the test uses db snapshot from dev env.
class ApplicationMigrationToolSpec extends FullIntegrationSpec {
  describe("ApplicationMigrationTool") {
    ignore("should convert old graphs") {
      assume(Files.exists(Paths.get("/Users/blutfullin/123123")))
      val proc = Process(
        command = "psql" ::
          "-h" :: "localhost" ::
          "-p" :: "5432" ::
          "-U" :: app.config.database.username ::
          "-w" :: "-f" :: "/Users/blutfullin/123123" ::
          "docker" :: Nil,
        cwd = None,
        extraEnv = "PGPASSWORD" -> app.config.database.password
      )
      println(proc)
      proc.lineStream_!.filter(_.contains("ERROR")).foreach(println)
      println("Executed the script!")
      println("Attempting to migrate the graphs")
      app.migrationTool.getAndRevive().unsafeRunSync()
      println("Migrated")
      val apps = DBApplicationRepository.allQ.to[List].transact(app.transactor).unsafeRunSync()
      apps.foreach(println)
      succeed
    }
  }
}