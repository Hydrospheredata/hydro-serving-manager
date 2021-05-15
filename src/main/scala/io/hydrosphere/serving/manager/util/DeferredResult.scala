package io.hydrosphere.serving.manager.util

import cats.implicits._
import cats.effect.{Concurrent, Deferred}

case class DeferredResult[F[_], T](
    started: T,
    completed: Deferred[F, T]
)

object DeferredResult {
  def completed[F[_], T](item: T)(implicit F: Concurrent[F]): F[DeferredResult[F, T]] =
    for {
      d <- F.deferred[T]
      _ <- d.complete(item)
    } yield DeferredResult(item, d)
}
