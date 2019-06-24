package io.hydrosphere.serving.manager.infrastructure.db

import cats.effect.Async
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.hydrosphere.serving.manager.util.AsyncUtil
import io.hydrosphere.slick.HydrospherePostgresDriver
import org.flywaydb.core.Flyway

import scala.concurrent.ExecutionContext


class DatabaseService(hikariConfig: HikariConfig)(implicit ec: ExecutionContext) {
  val dataSource = new HikariDataSource(hikariConfig)

  //Upgrade current schema
  private[this] val flyway = new Flyway()
  flyway.setDataSource(dataSource)
  flyway.setSchemas("hydro_serving")
  flyway.migrate()

  val driver = HydrospherePostgresDriver
  import driver.api._

  val db = Database.forDataSource(dataSource, Some(hikariConfig.getMaximumPoolSize))

  implicit class DelayedDB(db: driver.backend.DatabaseDef) {
    def task[F[_]: Async, R](a: DBIOAction[R, NoStream, Nothing]): F[R] = {
      AsyncUtil.futureAsync[F, R](db.run(a))
    }
  }

}
