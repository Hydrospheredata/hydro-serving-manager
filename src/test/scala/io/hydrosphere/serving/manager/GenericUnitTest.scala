package io.hydrosphere.serving.manager

import java.nio.file.{Path, Paths}
import cats.data.EitherT
import cats.effect.IO
import io.hydrosphere.serving.manager.domain.DomainError
import org.mockito.Mockito
import org.scalatest.funspec.AsyncFunSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, EitherValues}
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.{ExecutionContext, Future}

trait GenericUnitTest extends AsyncFunSpecLike with Matchers with EitherValues with MockitoSugar {
  implicit val ec = ExecutionContext.global
  implicit val cs = IO.contextShift(ec)

  def when[T](methodCall: T) = Mockito.when(methodCall)

  protected def ioAssert(body: => IO[Assertion]): Future[Assertion] =
    body.unsafeToFuture()

  protected def eitherTAssert(body: => EitherT[IO, DomainError, Assertion]): Future[Assertion] =
    eitherAssert(body.value)

  protected def eitherAssert(body: => IO[Either[DomainError, Assertion]]): Future[Assertion] =
    ioAssert {
      body.map {
        case Left(err) =>
          fail(err.message)
        case Right(asserts) =>
          asserts
      }
    }

  protected def getTestResourcePath(path: String): Path =
    Paths.get(this.getClass.getClassLoader.getResource(path).toURI)
}
