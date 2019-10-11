package io.hydrosphere.serving.manager

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import cats.effect._
import cats.implicits._
import com.spotify.docker.client.DefaultDockerClient
import com.zaxxer.hikari.HikariDataSource
import io.hydrosphere.serving.manager.config.{DockerClientConfig, ManagerConfiguration}
import io.hydrosphere.serving.manager.domain.clouddriver.CloudDriver
import io.hydrosphere.serving.manager.infrastructure.db.Database
import io.hydrosphere.serving.manager.infrastructure.db.repository.{DBApplicationRepository, DBModelRepository, DBModelVersionRepository}
import io.hydrosphere.serving.manager.infrastructure.grpc.{GrpcChannel, PredictionClient}
import io.hydrosphere.serving.manager.infrastructure.storage.StorageOps
import io.hydrosphere.serving.manager.util.ReflectionUtils
import io.hydrosphere.serving.manager.util.random.RNG
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object Boot extends IOApp with Logging {
  override def run(args: List[String]): IO[ExitCode] = IO.suspend {
    for {
      configuration <- ManagerConfiguration.load[IO]
      _ <- IO(logger.info(s"Config loaded:\n${ReflectionUtils.prettyPrint(configuration)}"))
      dockerClient <- IO(DefaultDockerClient.fromEnv().readTimeoutMillis(60 * 60 * 1000).build())
      dockerClientConfig <- DockerClientConfig
        .load[IO](DockerClientConfig.defaultConfigPath)
        .recover { case _ => DockerClientConfig() }
      _ <- IO(logger.info(s"Using docker client config: ${ReflectionUtils.prettyPrint(dockerClientConfig)}"))
      _ <- App.make[IO](configuration, dockerClient, dockerClientConfig).use { app =>
        app.httpServer.start() >> app.grpcServer.start() >> IO.never
      }
    } yield ExitCode.Success
  }
}
