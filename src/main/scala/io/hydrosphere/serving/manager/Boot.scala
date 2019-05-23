package io.hydrosphere.serving.manager

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import cats.effect._
import cats.implicits._
import com.spotify.docker.client.DefaultDockerClient
import io.hydrosphere.serving.manager.config.{DockerClientConfig, ManagerConfiguration}
import io.hydrosphere.serving.manager.infrastructure.grpc.{GrpcChannel, PredictionClient}
import io.hydrosphere.serving.manager.util.ReflectionUtils
import io.hydrosphere.serving.manager.util.random.RNG
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object Boot extends IOApp with Logging {
  override def run(args: List[String]): IO[ExitCode] = IO.suspend {
    implicit val system: ActorSystem = ActorSystem("manager")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val timeout: Timeout = Timeout(5.minute)
    implicit val serviceExecutionContext: ExecutionContext = ExecutionContext.global
    implicit val rng: RNG[IO] = RNG.default[IO].unsafeRunSync()

    for {
      configuration <- ManagerConfiguration.load[IO]
      _ <- IO(logger.info(s"Config loaded:\n${ReflectionUtils.prettyPrint(configuration)}"))
      dockerClient <- IO(DefaultDockerClient.fromEnv().build())
      dockerClientConfig <- DockerClientConfig.load[IO](DockerClientConfig.defaultConfigPath)
        .recover { case _ => DockerClientConfig() }
      _ <- IO(logger.info(s"Using docker client config: ${ReflectionUtils.prettyPrint(dockerClientConfig)}"))
      repos <- IO(new Repositories[IO](configuration))
      grpcCtor = GrpcChannel.plaintextFactory[IO]
      predictionCtor = PredictionClient.clientCtor[IO](grpcCtor)
      apis <- {
        Core.app[IO](configuration, repos, dockerClient, dockerClientConfig, predictionCtor)
      }
      (httpApi, grpcApi) = apis
      _ <- httpApi.start()
      _ <- IO(grpcApi.start())
      _ <- IO(logger.info(s"Started http service on port: ${configuration.application.port} and grpc service on ${configuration.application.grpcPort}"))
    } yield ExitCode.Success
  }
}