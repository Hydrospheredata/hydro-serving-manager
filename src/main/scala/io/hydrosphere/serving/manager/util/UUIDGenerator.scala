package io.hydrosphere.serving.manager.util

import java.util.UUID

import cats.effect.Sync

trait UUIDGenerator[F[_]] {
  def generate(): F[UUID]
}

object UUIDGenerator {
  def default[F[_]]()(implicit F: Sync[F]): UUIDGenerator[F] = new UUIDGenerator[F] {
    override def generate(): F[UUID] = F.delay {
      UUID.randomUUID()
    }
  }
}