package io.hydrosphere.serving.manager

import java.nio.file.{Path, Paths}

import cats.Applicative
import cats.data.NonEmptyList
import cats.effect.concurrent.Deferred
import cats.effect.{Clock, ContextShift, IO, Resource, Timer}
import io.hydrosphere.serving.manager.discovery.{DiscoveryEvent, DiscoveryPublisher}
import io.hydrosphere.serving.manager.domain.application.{ApplicationGraph, Variant, WeightedNode}
import io.hydrosphere.serving.manager.domain.contract._
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.infrastructure.grpc.PredictionClient
import io.hydrosphere.serving.manager.util.{UUIDGenerator, UnsafeLogging}
import io.hydrosphere.serving.manager.util.random.{NameGenerator, RNG}
import io.hydrosphere.serving.tensorflow.api.prediction_service.StatusResponse
import izumi.logstage.api.IzLogger
import izumi.logstage.api.Log.Level.Trace
import izumi.logstage.api.rendering.{RenderingOptions, StringRenderingPolicy}
import izumi.logstage.sink.ConsoleSink
import izumi.logstage.sink.slf4j.LogSinkLegacySlf4jImpl
import logstage.LogIO
import org.mockito.scalatest.AsyncMockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, AsyncFunSpecLike, EitherValues, OptionValues}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

trait GenericUnitTest
    extends AsyncFunSpecLike // Note(bulat) the deprecation fix messes up test discovery
    with Matchers
    with EitherValues
    with OptionValues
    with AsyncMockitoSugar
    with UnsafeLogging {
  implicit protected lazy val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit protected lazy val cs: ContextShift[IO]         = IO.contextShift(ec)
  implicit protected lazy val loggerF: LogIO[IO] =
    LogIO.fromLogger[IO](
      IzLogger(
        Trace,
        new LogSinkLegacySlf4jImpl(new StringRenderingPolicy(RenderingOptions.simple, None))
      )
    )
  implicit protected lazy val clock: Clock[IO] = Clock.create[IO]
  implicit protected lazy val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  implicit protected lazy val rng: RNG[IO]               = RNG.default[IO].unsafeRunSync()
  implicit protected lazy val nameGen: NameGenerator[IO] = NameGenerator.haiku[IO](rng)
  implicit protected lazy val uuidGen: UUIDGenerator[IO] = UUIDGenerator.default[IO]()

  protected lazy val dummyImage: DockerImage = DockerImage("a", "b")

  protected lazy val defaultContract: Contract = Contract(
    Signature(
      "predict",
      NonEmptyList.of(Field.Tensor("in1", DataType.DT_VARIANT, TensorShape.Dynamic, None)),
      NonEmptyList.of(Field.Tensor("in1", DataType.DT_VARIANT, TensorShape.Dynamic, None))
    )
  )

  protected def ioAssert(body: => IO[Assertion]): Future[Assertion] =
    body.unsafeToFuture()

  protected def getTestResourcePath(path: String): Path =
    Paths.get(this.getClass.getClassLoader.getResource(path).toURI)

  protected def mockPredictionFactory(
      statusResponse: IO[StatusResponse]
  ): PredictionClient.Factory[IO] = {
    val clientMock = mock[PredictionClient[IO]]
    when(clientMock.status())
      .thenReturn(statusResponse)

    val clientCtor = mock[PredictionClient.Factory[IO]]
    when(clientCtor.make(any, any)).thenReturn(Resource.pure[IO, PredictionClient[IO]](clientMock))
    clientCtor
  }

  protected def noopPublisher[F[_], T, K](implicit F: Applicative[F]): DiscoveryPublisher[F, T, K] =
    (_: DiscoveryEvent[T, K]) => F.unit

  protected def completedDeferred[F[_], T](value: T): IO[Deferred[IO, T]] =
    for {
      d <- Deferred[IO, T]
      _ <- d.complete(value)
    } yield d

  protected def singleModelGraph(modelVersion: ModelVersion.Internal): ApplicationGraph =
    ApplicationGraph(
      NonEmptyList.of(
        WeightedNode(
          NonEmptyList.of(Variant(modelVersion, None, 100)),
          modelVersion.modelContract.predict
        )
      ),
      modelVersion.modelContract.predict
    )
}
