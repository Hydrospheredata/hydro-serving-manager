package io.hydrosphere.serving.manager

import cats.effect._
import cats.implicits._
import com.spotify.docker.client.DefaultDockerClient
import io.hydrosphere.serving.manager.config.{DockerClientConfig, ManagerConfiguration}
import io.hydrosphere.serving.manager.infrastructure.docker.DockerdClient
import io.hydrosphere.serving.manager.util.{ReflectionUtils, UnsafeLogging}

object Boot extends IOApp with UnsafeLogging {
  override def run(args: List[String]): IO[ExitCode] = IO.suspend {
    for {
      configuration <- ManagerConfiguration.load[IO]
      _ <- IO(logger.info(s"Config loaded:\n${ReflectionUtils.prettyPrint(configuration)}"))
      dockerClient <- IO(DefaultDockerClient.fromEnv().readTimeoutMillis(60 * 60 * 1000).build())
      wrappedClient <- DockerdClient.create[IO](dockerClient)
      _ <- App.make[IO](configuration, wrappedClient).use { app =>
        app.httpServer.start() >> app.grpcServer.start() >> IO.never
      }
    } yield ExitCode.Success
  }
}
