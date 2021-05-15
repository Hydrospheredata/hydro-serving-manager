package io.hydrosphere.serving.manager.util

import cats.effect.Async

import scala.concurrent.{ExecutionContext, Future}

object AsyncUtil {
  def futureAsync[F[_], T](future: => Future[T])(implicit F: Async[F], ec: ExecutionContext): F[T] =
    Async[F].fromFuture(Async[F].delay(future))
}
