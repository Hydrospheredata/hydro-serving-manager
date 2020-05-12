package io.hydrosphere.serving.manager.infrastructure.db

import cats.effect.{Async, Blocker, ContextShift, Resource, Sync}
import cats.implicits._
import com.zaxxer.hikari.HikariDataSource
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor
import doobie.util.transactor.Transactor.Aux
import io.hydrosphere.serving.manager.config.HikariConfiguration
import javax.sql.DataSource
import org.flywaydb.core.Flyway

import scala.concurrent.ExecutionContext

object Database {
  type HikariTransactor[M[_]] = Transactor.Aux[M, HikariDataSource]

  def makeHikariDataSource[F[_]](
      hikariConfig: HikariConfiguration
  )(implicit F: Sync[F]): Resource[F, HikariDataSource] = {
    val hkds = F.delay {
      new HikariDataSource(HikariConfiguration.toConfig(hikariConfig))
    }
    Resource.make[F, HikariDataSource](hkds)(x => F.delay(x.close()))
  }

  def makeTransactor[F[_]](
      config: HikariConfiguration
  )(implicit
      F: Async[F],
      cs: ContextShift[F]
  ): Resource[F, HikariTransactor[F]] =
    for {
      hk         <- Database.makeHikariDataSource[F](config)
      connectEc  <- ExecutionContexts.fixedThreadPool[F](config.threadPoolSize)
      transactEc <- Blocker[F]
    } yield Transactor.fromDataSource[F](hk, connectEc, transactEc)
}
