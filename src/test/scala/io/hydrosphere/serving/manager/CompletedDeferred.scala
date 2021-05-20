package io.hydrosphere.serving.manager

import cats.Applicative
import cats.effect.{Deferred, IO}

object CompletedDeferred {
  case class CompletedDeferredMK[F[_]]() {
    def apply[T](item: T)(implicit F: Applicative[F]) =
      new Deferred[F, T] {
        override def get: F[T] = F.pure(item)

        override def tryGet: F[Option[T]] = F.pure(Some(item))

        override def complete(a: T): F[Boolean] = F.pure(true)
      }
  }

  def apply[F[_]]: CompletedDeferredMK[F] = CompletedDeferredMK[F]()

}
