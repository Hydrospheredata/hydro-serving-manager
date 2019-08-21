package io.hydrosphere.serving.manager.infrastructure.db.repository

import cats.effect.{Bracket, Sync}
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.domain.model.{Model, ModelRepository}

object DBModelRepository {

  case class ModelRow(
    model_id: Long,
    name: String
  )

  def toModel(mr: ModelRow) = Model(mr.model_id, mr.name)

  def fromModel(m:Model) = ModelRow(m.id, m.name)

  def allQ: doobie.Query0[ModelRow] = sql"SELECT * FROM hydro_serving.model".query[ModelRow]
  def getByNameQ(name: String): doobie.Query0[ModelRow] = sql"SELECT * FROM hydro_serving.model WHERE name = $name".query[ModelRow]
  def getByIdQ(id: Long): doobie.Query0[ModelRow] = sql"SELECT * FROM hydro_serving.model WHERE model_id = $id".query[ModelRow]
  def createQ(m: ModelRow): doobie.Update0 = sql"INSERT INTO hydro_serving.model (name) VALUES (${m.name})".update
  def updateQ(m: ModelRow): doobie.Update0 = sql"UPDATE hydro_serving.model SET name = ${m.name} WHERE model_id = ${m.model_id}".update
  def deleteQ(id: Long): doobie.Update0 = sql"DELETE FROM hydro_serving.model WHERE model_id = $id".update

  def make[F[_]]()(implicit F: Bracket[F, Throwable], tx: Transactor[F]): ModelRepository[F] = {
    new ModelRepository[F] {
      override def create(entity: Model): F[Model] = {
        for {
          id <- createQ(fromModel(entity)).withUniqueGeneratedKeys[Long]("model_id").transact(tx)
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

      override def update(value: Model): F[Int] = updateQ(fromModel(value)).run.transact(tx)

      override def delete(id: Long): F[Int] = deleteQ(id).run.transact(tx)
    }
  }
}