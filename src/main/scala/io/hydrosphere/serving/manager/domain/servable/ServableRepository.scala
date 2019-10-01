package io.hydrosphere.serving.manager.domain.servable

trait ServableRepository[F[_]] {
  def all(): fs2.Stream[F, Servable]

  def upsert(servable: Servable): F[Servable]

  def delete(name: String): F[Int]

  def get(name: String): F[Option[Servable]]

  def get(names: Seq[String]): fs2.Stream[F, Servable]
}