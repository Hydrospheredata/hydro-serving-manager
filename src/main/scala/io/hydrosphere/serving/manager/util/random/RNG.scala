package io.hydrosphere.serving.manager.util.random

import cats.implicits._
import java.util.Random

import cats.effect.Sync


trait RNG[F[_]] {
  def getInt(max: Int = Int.MaxValue): F[Int]
  def getLong(): F[Long]
  def getItem[T](x: Seq[T]): F[T]
}

object RNG {
  def apply[F[_]](implicit F: RNG[F]): RNG[F] = F

  def default[F[_]](implicit F: Sync[F]): F[RNG[F]] = F.delay {
    val random = new Random()
    new RNG[F] {
      override def getInt(max: Int): F[Int] = F.delay(random.nextInt(max))

      override def getLong(): F[Long] = F.delay(random.nextLong())

      override def getItem[T](x: Seq[T]): F[T] = {
        for {
          i <- getInt(x.length)
        } yield x(i)
      }
    }
  }
}
