package io.hydrosphere.serving.manager.infrastructure.db

import cats.effect.{Async, ContextShift, Resource, Sync}
import cats.implicits._
import com.zaxxer.hikari.HikariDataSource
import doobie.hikari.HikariTransactor
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.config.HikariConfiguration
import javax.sql.DataSource
import org.flywaydb.core.Flyway

import scala.concurrent.ExecutionContext

object Database {
  def makeHikariDataSource[F[_]](hikariConfig: HikariConfiguration)(implicit F: Sync[F]): Resource[F, HikariDataSource] = {
    for {
      config <- Resource.liftF(F.delay(HikariConfiguration.toConfig(hikariConfig)))
      dataSource <- Resource.make[F, HikariDataSource](F.delay(new HikariDataSource(config)))(x => F.delay(x.close()))
    } yield dataSource
  }

  def makeTransactor[F[_]](
    dataSource: HikariDataSource,
    connectEc: ExecutionContext,
    transactEc: ExecutionContext
  )(
    implicit F: Async[F],
    cs: ContextShift[F]
  ): F[HikariTransactor[F]] = {
    F.delay {
      HikariTransactor.apply[F](
        dataSource,
        connectEc,
        transactEc
      )
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
