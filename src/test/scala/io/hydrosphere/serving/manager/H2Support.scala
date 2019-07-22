package io.hydrosphere.serving.manager

import cats.effect.{Async, ContextShift, Resource}
import doobie.h2.H2Transactor
import doobie.util.ExecutionContexts
import io.hydrosphere.serving.manager.infrastructure.db.Database

trait H2Support {

  def makeH2Transactor[F[_] : Async : ContextShift]() = for {
    ce <- ExecutionContexts.fixedThreadPool[F](32) // our connect EC
    te <- ExecutionContexts.cachedThreadPool[F] // our transaction EC
    xa <- H2Transactor.newH2Transactor[F](
      "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", // connect URL
      "sa", // username
      "", // password
      ce, // await connection here
      te // execute JDBC operations here
    )
    res <- Resource.liftF(Database.makeFlyway(xa))
    _ <- Resource.liftF(res.migrate())
  } yield xa

}
