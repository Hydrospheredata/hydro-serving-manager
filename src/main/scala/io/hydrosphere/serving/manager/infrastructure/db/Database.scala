package io.hydrosphere.serving.manager.infrastructure.db

import cats.effect.{Async, Blocker, ContextShift, Resource, Sync}
import cats.implicits._
import com.zaxxer.hikari.HikariDataSource
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.config.HikariConfiguration
import javax.sql.DataSource
import org.flywaydb.core.Flyway

import scala.concurrent.ExecutionContext

object Database {
  type HikariTransactor[M[_]] = Transactor.Aux[M, HikariDataSource]

  def makeHikariDataSource[F[_]](hikariConfig: HikariConfiguration)(implicit F: Sync[F]): Resource[F, HikariDataSource] = {
    val hkds = F.delay {
      new HikariDataSource(HikariConfiguration.toConfig(hikariConfig))
    }
    Resource.make[F, HikariDataSource](hkds)(x => F.delay(x.close()))
  }

  def makeTransactor[F[_]](
    dataSource: HikariDataSource,
    transactEc: ExecutionContext,
    blocker: Blocker
  )(
    implicit F: Async[F],
    cs: ContextShift[F]
  ): F[HikariTransactor[F]] = {
    F.delay {
      Transactor.fromDataSource[F](dataSource, transactEc, blocker)
    }
  }

  def makeFlyway[F[_], D <: DataSource](
    tx: Transactor.Aux[F, D],
  )(implicit F: Async[F]): F[FlywayClient[F]] = {
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
}
