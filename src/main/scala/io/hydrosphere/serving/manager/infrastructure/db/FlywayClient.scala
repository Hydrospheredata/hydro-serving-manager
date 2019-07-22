package io.hydrosphere.serving.manager.infrastructure.db

import cats.effect.Sync
import org.flywaydb.core.Flyway

trait FlywayClient[F[_]] {
  def migrate(): F[Int]
  def validate(): F[Unit]
}

object FlywayClient {
  def default[F[_]](fl: Flyway)(implicit F: Sync[F]): FlywayClient[F] = {
    new FlywayClient[F] {
      override def migrate(): F[Int] = F.delay(fl.migrate())

      override def validate(): F[Unit] = F.delay(fl.validate())
    }
  }
}
