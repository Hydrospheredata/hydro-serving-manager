package io.hydrosphere.serving.manager

import cats.effect._
import cats.implicits._
import com.spotify.docker.client.DefaultDockerClient
import io.hydrosphere.serving.manager.config.ManagerConfiguration
import io.hydrosphere.serving.manager.infrastructure.docker.DockerdClient
import org.apache.logging.log4j.scala.Logging

object Boot extends IOApp with Logging {
  override def run(args: List[String]): IO[ExitCode] =
    for {
      configuration <- ManagerConfiguration.load[IO]()
      _             <- IO(logger.info(configuration.show))
      dockerClient  <- IO(DefaultDockerClient.fromEnv().readTimeoutMillis(60 * 60 * 1000).build())
      wrappedClient <- DockerdClient.create[IO](dockerClient)
      _ <- App.make[IO](configuration, wrappedClient).use { app =>
        app.migrationTool.getAndRecover() >>
          app.httpServer.start() >>
          app.grpcServer.start() >>
          app.applicationMonitoring.start() >>
          app.servableMonitoring.start() >> IO.never
      }
    } yield ExitCode.Success
}
