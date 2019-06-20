package io.hydrosphere.serving.manager.infrastructure.docker.logging

import cats.effect.ConcurrentEffect
import cats.effect.implicits._
import cats.implicits._
import com.spotify.docker.client.ProgressHandler
import com.spotify.docker.client.messages.ProgressMessage
import fs2.concurrent.{SignallingRef, Topic}

case class BuildLog(
  modelVersionId: Long,
  id: Option[String] = None,
  message: Option[String] = None,
  error: Option[String] = None
)

trait LogSink[F[_]] {
  def handler: F[ProgressHandler]

  def logs: fs2.Stream[F, BuildLog]

  def finish: F[Unit]
}

object LogSink {
  def modelBuildSink[F[_]](modelVersionId: Long)(implicit F: ConcurrentEffect[F]) = {
    for {
      queue <- Topic[F, BuildLog](BuildLog(modelVersionId))
      signal <- SignallingRef[F, Boolean](false)
    } yield new LogSink[F] {
      override def handler: F[ProgressHandler] = F.delay {
        message: ProgressMessage => {
          // many of the `message` fields are nullable. Take care!
          queue.publish1(BuildLog(
            modelVersionId,
            Option(message.id()),
            Option(message.stream()),
            Option(message.error())
          )).toIO.unsafeRunSync()
        }
      }

      override def logs: fs2.Stream[F, BuildLog] = queue.subscribe(128).interruptWhen(signal)

      override def finish: F[Unit] = signal.set(true)
    }
  }
}