package io.hydrosphere.serving.manager.infrastructure.db

import cats.effect.{Async, Sync}
import cats.implicits._
import doobie.util.transactor.Transactor
import javax.sql.DataSource
import org.flywaydb.core.Flyway

trait FlywayClient[F[_]] {
  def migrate(): F[Int]
  def validate(): F[Unit]
}

object FlywayClient {
  def forTransactor[F[_], D <: DataSource](
      tx: Transactor.Aux[F, D]
  )(implicit F: Async[F]): F[FlywayClient[F]] =
    for {
      fl <- tx.configure { x =>
        F.delay {
          val flyway = new Flyway()
          flyway.setDataSource(x)
          flyway.setSchemas("hydro_serving")
          FlywayClient(flyway)
        }
      }
    } yield fl
  def apply[F[_]](fl: Flyway)(implicit F: Sync[F]): FlywayClient[F] =
    new FlywayClient[F] {
      override def migrate(): F[Int] = F.delay(fl.migrate())

      override def validate(): F[Unit] = F.delay(fl.validate())
    }
}
