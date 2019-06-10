package io.hydrosphere.serving.manager.util

import cats.implicits._
import cats.effect.Concurrent
import cats.effect.concurrent.Deferred

case class DeferredResult[F[_], T](
  started: T,
  completed: Deferred[F,T]
)

object DeferredResult {
  def completed[F[_] : Concurrent, T](item: T): F[DeferredResult[F, T]] = {
    for {
      d <- Deferred[F, T]
      _ <- d.complete(item)
    } yield DeferredResult(item, d)
  }
}
