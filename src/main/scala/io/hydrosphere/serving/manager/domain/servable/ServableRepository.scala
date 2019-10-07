package io.hydrosphere.serving.manager.domain.servable

trait ServableRepository[F[_]] {
  def all(): F[List[Servable.GenericServable]]

  def upsert(servable: Servable.GenericServable): F[Servable.GenericServable]

  def delete(name: String): F[Int]

  def get(name: String): F[Option[Servable.GenericServable]]

  def get(names: Seq[String]): F[List[Servable.GenericServable]]

  def findForModelVersion(versionId: Long): F[List[Servable.GenericServable]]
}