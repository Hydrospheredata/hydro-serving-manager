package io.hydrosphere.serving.manager.util

import cats.implicits._
import cats.effect.implicits._
import cats.effect.{Concurrent, Resource}
import fs2.Pipe
import fs2.concurrent.Topic.Closed
import fs2.concurrent.{SignallingRef, Topic}

trait LogTopic[F[_]] extends Topic[F, String] {

  /**
    * Returns topic events starting from specified id.
    *
    * @param id
    * @return
    */
  def subscribeFrom(id: Long): fs2.Stream[F, String]

  def dispose: F[Unit]
}

object LogTopic {
  def withPersistingSink[F[_]](
      ps: Pipe[F, String, Unit]
  )(implicit F: Concurrent[F]): F[LogTopic[F]] =
    for {
      underlying <- Topic[F, String]
      counted = underlying.subscribe(Int.MaxValue).zipWithIndex
      pStopSignal    <- SignallingRef[F, Boolean](false)
      persistenceFbr <- counted.map(_._1).through(ps).interruptWhen(pStopSignal).compile.drain.start
    } yield new LogTopic[F] {
      override def publish: Pipe[F, String, Nothing] = underlying.publish

      override def publish1(a: String): F[Either[Closed, Unit]] = underlying.publish1(a)

      override def subscribe(maxQueued: Int): fs2.Stream[F, String] =
        underlying.subscribe(maxQueued)

      override def subscribers: fs2.Stream[F, Int] = underlying.subscribers

      override def subscribeFrom(id: Long): fs2.Stream[F, String] =
        counted.collect {
          case (x, num) if num >= id => x
        }

      override def dispose: F[Unit] = pStopSignal.set(true)

      override def subscribeAwait(maxQueued: Int): Resource[F, fs2.Stream[F, String]] =
        underlying.subscribeAwait(maxQueued)

      override def close: F[Either[Closed, Unit]] = underlying.close

      override def isClosed: F[Boolean] = underlying.isClosed

      override def closed: F[Unit] = underlying.closed
    }
}
