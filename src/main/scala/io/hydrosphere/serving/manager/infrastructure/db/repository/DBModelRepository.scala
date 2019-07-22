package io.hydrosphere.serving.manager.infrastructure.db.repository

import cats.effect.Sync
import cats.implicits._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.domain.model.{Model, ModelRepository}

object DBModelRepository {

  case class ModelRow(
    model_id: Long,
    name: String
  )

  def toModel(mr: ModelRow) = Model(mr.model_id, mr.name)

  final val modelTableName = sql"hydro_serving.model"

  def allQ = sql"SELECT * FROM $modelTableName"

  def getByNameQ(name: String) = sql"$allQ WHERE name = $name"

  def getByIdQ(id: Long) = sql"$allQ WHERE id = $id"

  def createQ(m: Model) = sql"INSERT INTO $modelTableName (name) VALUES (${m.name})"

  def updateQ(m: Model) = sql"UPDATE $modelTableName set name = ${m.name} WHERE id = ${m.id}"

  def deleteQ(id: Long) = sql"DELETE FROM $modelTableName WHERE id = id"

  def make[F[_] : Sync](tx: Transactor[F]) = {
    new ModelRepository[F] {
      override def create(entity: Model): F[Model] = {
        for {
          id <- createQ(entity).update.withUniqueGeneratedKeys[Long]("model_id").transact(tx)
        } yield Model(id, entity.name)
      }

      override def get(id: Long): F[Option[Model]] = {
        for {
          row <- getByIdQ(id).query[ModelRow].option.transact(tx)
        } yield row.map(toModel)
      }

      override def all(): F[Seq[Model]] = {
        for {
          row <- allQ.query[ModelRow].to[Seq].transact(tx)
        } yield row.map(toModel)
      }

      override def get(name: String): F[Option[Model]] = {
        for {
          row <- getByNameQ(name).query[ModelRow].option.transact(tx)
        } yield row.map(toModel)
      }

      override def update(value: Model): F[Int] = updateQ(value).update.run.transact(tx)

      override def delete(id: Long): F[Int] = deleteQ(id).update.run.transact(tx)
    }
  }
}