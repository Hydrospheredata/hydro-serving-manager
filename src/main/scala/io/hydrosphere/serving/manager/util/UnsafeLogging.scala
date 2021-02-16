package io.hydrosphere.serving.manager.util

import cats.effect.Sync
import org.slf4j.{Logger, LoggerFactory}

trait UnsafeLogging {
  protected final val logger = LoggerFactory.getLogger(this.getClass)
}

trait Logging[F[_]] {
  def info(msg: String): F[Unit]
  def info(msg: String, ex: Throwable): F[Unit]

  def warn(msg: String): F[Unit]
  def warn(msg: String, ex: Throwable): F[Unit]

  def error(msg: String): F[Unit]
  def error(msg: String, ex: Throwable): F[Unit]

  def debug(msg: String): F[Unit]
  def debug(msg: String, ex: Throwable): F[Unit]
}

object Logging {
  def fromSlf4j[F[_]](logger: Logger)(implicit F: Sync[F]): Logging[F] = new Logging[F] {
    override def info(msg: String): F[Unit] = F.delay(logger.info(msg))
    override def info(msg: String, ex: Throwable): F[Unit] = F.delay(logger.info(msg, ex))

    override def warn(msg: String): F[Unit] = F.delay(logger.warn(msg))
    override def warn(msg: String, ex: Throwable): F[Unit] = F.delay(logger.warn(msg, ex))

    override def error(msg: String): F[Unit] = F.delay(logger.error(msg))
    override def error(msg: String, ex: Throwable): F[Unit] = F.delay(logger.error(msg, ex))

    override def debug(msg: String): F[Unit] = F.delay(logger.debug(msg))
    override def debug(msg: String, ex: Throwable): F[Unit] = F.delay(logger.debug(msg, ex))
  }
}