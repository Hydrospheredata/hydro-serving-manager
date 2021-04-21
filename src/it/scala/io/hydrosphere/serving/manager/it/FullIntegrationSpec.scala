package io.hydrosphere.serving.manager.it

import java.nio.file.{Files, Path, Paths}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import cats.data.EitherT
import cats.effect.IO
import io.hydrosphere.serving.manager._
import io.hydrosphere.serving.manager.config.{DockerClientConfig, ManagerConfiguration}
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.infrastructure.docker.DockerdClient
import io.hydrosphere.serving.manager.infrastructure.grpc.GrpcChannel
import io.hydrosphere.serving.manager.util.TarGzUtils
import io.hydrosphere.serving.manager.util.random.RNG
import org.scalatest._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait FullIntegrationSpec extends DatabaseAccessIT with BeforeAndAfterEach {

  implicit val system       = ActorSystem("fullIT-system")
  implicit val materializer = ActorMaterializer()
  implicit val ex           = ExecutionContext.global
  implicit val contextShift = IO.contextShift(ex)
  implicit val timeout      = Timeout(5.minute)
  implicit val timer        = IO.timer(ex)
  implicit val rng          = RNG.default[IO].unsafeRunSync()

  val dummyImage = DockerImage(
    name = "hydrosphere/serving-runtime-dummy",
    tag = "latest"
  )

  private[this] val originalConfiguration = ManagerConfiguration.load[IO]
  def configuration                       = originalConfiguration.unsafeRunSync()

  val wrappedDockerClient               = DockerdClient.create[IO](dockerClient).unsafeRunSync()
  val allocatedApp                      = App.make[IO](configuration, wrappedDockerClient).allocated.unsafeRunSync()
  val app: App[IO]                      = allocatedApp._1
  val appFree: IO[Unit]                 = allocatedApp._2
  val grpcCtor: GrpcChannel.Factory[IO] = GrpcChannel.plaintextFactory[IO]

  override def afterAll(): Unit = {
    app.grpcServer.shutdown().unsafeRunSync()
    system.terminate()
    appFree.unsafeRunSync()
    super.afterAll()
  }

  protected def packModel(str: String): Path = {
    val temptar = Files.createTempFile("packedModel", ".tar.gz")
    TarGzUtils.compressFolder(Paths.get(getClass.getResource(str).toURI), temptar)
    temptar
  }

  protected def eitherAssert(body: => IO[Either[DomainError, Assertion]]): Future[Assertion] =
    body
      .map {
        case Left(err) =>
          fail(err.message)
        case Right(asserts) =>
          asserts
      }
      .unsafeToFuture()

  protected def eitherTAssert(body: => EitherT[IO, DomainError, Assertion]): Future[Assertion] =
    eitherAssert(body.value)

  protected def ioAssert(body: => IO[Assertion]): Future[Assertion] =
    body.unsafeToFuture()
}
