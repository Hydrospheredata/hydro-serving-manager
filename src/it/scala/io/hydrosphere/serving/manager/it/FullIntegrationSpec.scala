package io.hydrosphere.serving.manager.it

import java.nio.file.{Files, Path, Paths}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import cats.data.EitherT
import cats.effect.IO
import cats.instances.list._
import cats.syntax.traverse._
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import io.grpc.Server
import io.hydrosphere.serving.manager._
import io.hydrosphere.serving.manager.api.grpc.GrpcApiServer
import io.hydrosphere.serving.manager.api.http.HttpApiServer
import io.hydrosphere.serving.manager.config.{DockerClientConfig, ManagerConfiguration}
import io.hydrosphere.serving.manager.discovery.application.{ApplicationDiscoveryHub, ObservedApplicationDiscoveryHub}
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.application.Application
import io.hydrosphere.serving.manager.domain.application.Application.ReadyApp
import io.hydrosphere.serving.manager.domain.application.ApplicationService.Internals
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.infrastructure.grpc.{GrpcChannel, PredictionClient}
import io.hydrosphere.serving.manager.util.TarGzUtils
import io.hydrosphere.serving.manager.util.random.RNG
import org.scalatest._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


trait FullIntegrationSpec extends DatabaseAccessIT
  with BeforeAndAfterEach {

  implicit val system = ActorSystem("fullIT-system")
  implicit val materializer = ActorMaterializer()
  implicit val ex = ExecutionContext.global
  implicit val contextShift = IO.contextShift(ex)
  implicit val timeout = Timeout(5.minute)
  implicit val timer = IO.timer(ex)
  implicit val rng = RNG.default[IO].unsafeRunSync()

  val dummyImage = DockerImage(
    name = "hydrosphere/serving-runtime-dummy",
    tag = "latest"
  )

  private[this] var rawConfig = ConfigFactory.load()
  rawConfig = rawConfig.withValue(
    "database",
    rawConfig.getConfig("database").withValue(
      "jdbcUrl",
      ConfigValueFactory.fromAnyRef(s"jdbc:postgresql://localhost:5432/docker")
    ).root()
  )

  private[this] val originalConfiguration = ManagerConfiguration.load[IO]

  def configuration = originalConfiguration.unsafeRunSync()

  var repositories: Repositories[IO] = _
  var cloudDriver: CloudDriver[IO] = _
  var managerServices: Services[IO] = _
  var discoveryHub: ObservedApplicationDiscoveryHub[IO] = _
  var managerApi: HttpApiServer[IO] = _
  var managerGRPC: Server = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    cloudDriver = CloudDriver.fromConfig[IO](configuration.cloudDriver, configuration.dockerRepository)
    repositories = new Repositories[IO](configuration)
      val discoveryHubIO = for {
      observed <- ApplicationDiscoveryHub.observed[IO]
      apps     <- repositories.applicationRepository.all()
      needToDiscover = apps.flatMap { app =>
        app.status match {
          case _: Application.Ready =>
            Internals.toServingApp(app.asInstanceOf[ReadyApp]) :: Nil
          case _ => Nil
        }
      }
      _ <- needToDiscover.traverse(observed.added)
    } yield observed

    discoveryHub = discoveryHubIO.unsafeRunSync()
    val channelCtor = GrpcChannel.plaintextFactory[IO]
    val predictionClient = PredictionClient.clientCtor[IO](channelCtor)

    managerServices = new Services[IO](
      discoveryHub,
      repositories,
      configuration,
      dockerClient,
      DockerClientConfig(),
      cloudDriver,
      predictionClient
    )
    repositories = new Repositories(configuration)
    managerApi = new HttpApiServer(repositories, managerServices, configuration)
    managerGRPC = GrpcApiServer[IO](repositories, managerServices, configuration, discoveryHub)
    managerApi.start()
    managerGRPC.start()
  }

  override def afterAll(): Unit = {
    managerGRPC.shutdown()
    system.terminate()
    super.afterAll()
  }

  protected def packModel(str: String): Path = {
    val temptar = Files.createTempFile("packedModel", ".tar.gz")
    TarGzUtils.compressFolder(Paths.get(getClass.getResource(str).toURI), temptar)
    temptar
  }

  protected def eitherAssert(body: => IO[Either[DomainError, Assertion]]): Future[Assertion] = {
    body.map {
      case Left(err) =>
        fail(err.message)
      case Right(asserts) =>
        asserts
    }.unsafeToFuture()
  }

  protected def eitherTAssert(body: => EitherT[IO, DomainError, Assertion]): Future[Assertion] = {
    eitherAssert(body.value)
  }

  protected def ioAssert(body: => IO[Assertion]): Future[Assertion] = {
    body.unsafeToFuture()
  }
}