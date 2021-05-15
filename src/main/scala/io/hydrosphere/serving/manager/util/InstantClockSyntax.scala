package io.hydrosphere.serving.manager.util

import java.time.Instant
import java.util.concurrent.TimeUnit

import cats.Functor
import cats.effect.Clock
import cats.implicits._

object InstantClockSyntax {

  implicit final class ClockToInstant[F[_]](private val clock: Clock[F])(implicit F: Functor[F]) {

    /***
      * Returns the result of `realTime` wrapped with `java.time.Instant`
      * @return
      */
    def instant(): F[Instant] =
      for {
        millis <- clock.realTime
      } yield Instant.ofEpochMilli(millis.toMillis)
  }

}
