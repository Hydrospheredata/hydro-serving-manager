package io.hydrosphere.serving.manager.infrastructure.db.repository

import cats.effect.{Bracket, Sync}
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

  final val modelTableName = "hydro_serving.model"

  def allQ: doobie.Query0[ModelRow] = sql"SELECT * FROM $modelTableName".query[ModelRow]

  def getByNameQ(name: String): doobie.Query0[ModelRow] = sql"${allQ.sql} WHERE name = $name".query[ModelRow]

  def getByIdQ(id: Long): doobie.Query0[ModelRow] = sql"${allQ.sql} WHERE id = $id".query[ModelRow]

  def createQ(m: Model): doobie.Update0 = sql"INSERT INTO $modelTableName (name) VALUES (${m.name})".update

  def updateQ(m: Model): doobie.Update0 = sql"UPDATE $modelTableName SET name = ${m.name} WHERE id = ${m.id}".update

  def deleteQ(id: Long): doobie.Update0 = sql"DELETE FROM $modelTableName WHERE id = id".update

  def make[F[_]](tx: Transactor[F])(implicit F: Bracket[F, Throwable]): ModelRepository[F] = {
    new ModelRepository[F] {
      override def create(entity: Model): F[Model] = {
        for {
          id <- createQ(entity).withUniqueGeneratedKeys[Long]("model_id").transact(tx)
        } yield Model(id, entity.name)
      }

      override def get(id: Long): F[Option[Model]] = {
        for {
          row <- getByIdQ(id).option.transact(tx)
        } yield row.map(toModel)
      }

      override def all(): F[Seq[Model]] = {
        for {
          row <- allQ.to[Seq].transact(tx)
        } yield row.map(toModel)
      }

      override def get(name: String): F[Option[Model]] = {
        for {
          row <- getByNameQ(name).option.transact(tx)
        } yield row.map(toModel)
      }

      override def update(value: Model): F[Int] = updateQ(value).run.transact(tx)

      override def delete(id: Long): F[Int] = deleteQ(id).run.transact(tx)
    }
  }
}