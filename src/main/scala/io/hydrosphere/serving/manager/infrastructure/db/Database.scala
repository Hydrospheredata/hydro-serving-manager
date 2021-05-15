package io.hydrosphere.serving.manager.infrastructure.db

import cats.effect.{Async, Resource}
import cats.implicits._
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.config.HikariConfiguration
import org.flywaydb.core.Flyway

import javax.sql.DataSource

object Database {
  def makeTransactor[F[_]](
      hikariConfig: HikariConfiguration
  )(implicit F: Async[F]): Resource[F, HikariTransactor[F]] = {
    val config = HikariConfiguration.toConfig(hikariConfig)
    for {
      connectEc <- ExecutionContexts.fixedThreadPool[F](hikariConfig.maximumPoolSize)
      tx        <- HikariTransactor.fromHikariConfig(config, connectEc)
    } yield tx
  }

  def makeFlyway[F[_], D <: DataSource](
      tx: Transactor.Aux[F, D]
  )(implicit F: Async[F]): F[FlywayClient[F]] =
    for {
      fl <- tx.configure { x =>
        F.delay {
          val flyway = new Flyway()
          flyway.setDataSource(x)
          flyway.setSchemas("hydro_serving")
          FlywayClient.default(flyway)
        }
      }
    } yield fl
}
