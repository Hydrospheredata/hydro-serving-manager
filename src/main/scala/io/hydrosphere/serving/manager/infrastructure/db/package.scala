package io.hydrosphere.serving.manager.infrastructure

import cats.effect.{Async, ContextShift, Resource, Sync}
import com.zaxxer.hikari.HikariDataSource
import doobie.hikari.HikariTransactor
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.config.HikariConfiguration
import org.flywaydb.core.Flyway



import cats.effect._
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.hikari._

import scala.concurrent.ExecutionContext

package object db {
  def makeDataSource[F[_]](hikariConfig: HikariConfiguration)(implicit F: Sync[F]): Resource[F, HikariDataSource] = {
    for {
      config <- Resource.liftF(F.delay(HikariConfiguration.toConfig(hikariConfig)))
      dataSource <- Resource.make[F, HikariDataSource](F.delay(new HikariDataSource(config)))(x => F.delay(x.close()))
    } yield dataSource
  }


  def makeFlyway[F[_]](
    dataSource: HikariDataSource,
    schema: String = "hydro_serving"
  )(implicit F: Sync[F]): F[FlywayClient[F]] = {
    F.delay {
      val flyway = new Flyway()
      flyway.setDataSource(dataSource)
      flyway.setSchemas("hydro_serving")
      FlywayClient.default(flyway)
    }
  }

  def makeTransactor[F[_]](
    dataSource: HikariDataSource,
    connectEc: ExecutionContext,
    transactEc: ExecutionContext
  )(
    implicit F: Async[F],
    cs: ContextShift[F]
  ): F[Transactor[F]] = {
    F.delay {
      HikariTransactor.apply[F](
        dataSource,
        connectEc,
        transactEc
      )
    }
  }
}
