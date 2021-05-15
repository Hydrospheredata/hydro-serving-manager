package io.hydrosphere.serving.manager.domain.model_build

import cats.data.OptionT
import cats.effect.Ref
import cats.effect.implicits._
import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import cats.implicits._
import com.spotify.docker.client.ProgressHandler
import com.spotify.docker.client.messages.ProgressMessage
import fs2.concurrent.{SignallingRef, Topic}
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import org.apache.logging.log4j.scala.Logging

import scala.collection.mutable.ListBuffer

trait BuildLoggingService[F[_]] {
  def makeLogger(modelVersion: ModelVersion.Internal): F[ProgressHandler]

  def finishLogging(modelVersion: Long): F[Option[Unit]]

  def getLogs(modelVersionId: Long, sinceLine: Int = 0): F[Option[fs2.Stream[F, String]]]
}

object BuildLoggingService extends Logging {
  type LoggingState[F[_]] = (Topic[F, String], SignallingRef[F, Boolean], ListBuffer[String])

  def make[F[_]]()(implicit
      F: Async[F],
      buildLogRepository: BuildLogRepository[F]
  ): F[BuildLoggingService[F]] =
    for {
      state <- Ref.of[F, Map[Long, LoggingState[F]]](
        Map.empty
      )
    } yield new BuildLoggingService[F] {
      override def makeLogger(modelVersion: ModelVersion.Internal): F[ProgressHandler] =
        for {
          signal <- SignallingRef[F, Boolean](false)
          topic  <- Topic[F, String]
          buf = ListBuffer.empty[String]
          _ <-
            topic
              .subscribe(Int.MaxValue)
              .interruptWhen(signal)
              .evalMap(l => F.delay(buf += l).void)
              .compile
              .drain
              .start
          _      <- state.update(o => o ++ Map(modelVersion.id -> (topic, signal, buf)))
          logger <- DockerLogger.make(topic)
        } yield logger

      override def getLogs(
          modelVersionId: Long,
          sinceLine: Int
      ): F[Option[fs2.Stream[F, String]]] = {
        val dbLogs = for {
          logs <- OptionT(buildLogRepository.get(modelVersionId))
        } yield logs.drop(sinceLine)

        val runningLogs = for {
          state <- OptionT.liftF(state.get)
          row   <- OptionT.fromOption[F](state.get(modelVersionId))
          (topic, signal, buf) = row
          trimmedBuf = fs2.Stream.emits[F, String](
            buf.toList.drop(sinceLine).init
          ) // `init` because `topic.subscribe` puts the latest message in the stream
          sub = topic.subscribe(32).interruptWhen(signal)
        } yield trimmedBuf ++ sub

        dbLogs.orElse(runningLogs).value
      }

      override def finishLogging(modelVersionId: Long): F[Option[Unit]] = {
        val f = for {
          stateMap <- OptionT.liftF(state.get)
          row      <- OptionT.fromOption[F](stateMap.get(modelVersionId))
          (_, signal, buf) = row
          _ <- OptionT.liftF(signal.set(true))
          _ <- OptionT.liftF(buildLogRepository.add(modelVersionId, buf.toList))
          _ <- OptionT.liftF(state.update(x => x.view.filterKeys(_ != modelVersionId).toMap))
        } yield ()
        f.value
      }
    }
}

object DockerLogger {
  final val ESC_CODE = 0x1b

  def make[F[_]](topic: Topic[F, String])(implicit F: Async[F]): F[ProgressHandler] =
    Dispatcher[F].use { dispatcher =>
      F.delay {
        new ProgressHandler {
          override def progress(message: ProgressMessage): Unit = {
            val maybeMsg = Option(message.error())
              .orElse(Option(message.stream()))
              .orElse(Option(message.status()))
            maybeMsg.foreach { msg =>
              val trimmed = msg.trim
              if (trimmed.nonEmpty)
                dispatcher.unsafeRunSync(topic.publish1(trimmed))
            }
          }
        }
      }
    }
}
