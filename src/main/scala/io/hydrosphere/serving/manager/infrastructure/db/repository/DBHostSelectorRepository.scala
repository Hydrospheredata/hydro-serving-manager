package io.hydrosphere.serving.manager.infrastructure.db.repository

import cats._, cats.data._, cats.implicits._
import doobie._, doobie.implicits._

import org.postgresql.util.PGobject
import cats.effect.{Bracket, Sync}
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.domain.host_selector.{HostSelector, HostSelectorRepository}

object DBHostSelectorRepository {

  case class HostSelectorRow(
    host_selector_id: Long,
    name: String,
    node_selector: Map[String, String]
  )

  def toHostSelector(hs: HostSelectorRow) = HostSelector(hs.host_selector_id, hs.name, hs.node_selector)

  def allQ: doobie.Query0[HostSelectorRow] = sql"SELECT * FROM hydro_serving.host_selector".query[HostSelectorRow]

  def insertQ(entity: HostSelector): doobie.Update0 = sql"INSERT INTO hydro_serving.host_selector (name, node_selector) VALUES (${entity.name}, ${entity.nodeSelector})".update

  def getByIdQ(id: Long): doobie.Query0[HostSelectorRow] = sql"SELECT * FROM hydro_serving.host_selector WHERE id = $id".query[HostSelectorRow]

  def getByNameQ(name: String): doobie.Query0[HostSelectorRow] = sql"SELECT * FROM hydro_serving.host_selector WHERE name = $name".query[HostSelectorRow]

  def deleteQ(id: Long): doobie.Update0 = sql"DELETE FROM hydro_serving.host_selector WHERE id = $id".update

  def make[F[_]]()(implicit F: Bracket[F, Throwable], tx: Transactor[F]): HostSelectorRepository[F] = {
    new HostSelectorRepository[F] {
      override def create(entity: HostSelector): F[HostSelector] = {
        for {
          id <- insertQ(entity).withUniqueGeneratedKeys[Long]("id").transact(tx)
        } yield HostSelector(id, entity.name, entity.nodeSelector)
      }

      override def get(id: Long): F[Option[HostSelector]] = {
        for {
          row <- getByIdQ(id).option.transact(tx)
        } yield row.map(toHostSelector)
      }

      override def get(name: String): F[Option[HostSelector]] = {
        for {
          row <- getByNameQ(name).option.transact(tx)
        } yield row.map(toHostSelector)
      }

      override def delete(id: Long): F[Int] = deleteQ(id).run.transact(tx)

      override def all(): F[List[HostSelector]] = {
        for {
          rows <- allQ.to[List].transact(tx)
        } yield rows.map(toHostSelector)
      }
    }
  }
}