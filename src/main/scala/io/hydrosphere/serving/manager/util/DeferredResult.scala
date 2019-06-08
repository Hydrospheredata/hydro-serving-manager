package io.hydrosphere.serving.manager.util

import cats.effect.concurrent.Deferred

case class DeferredResult[F[_], T](
  started: T,
  completed: Deferred[F,T]
)
