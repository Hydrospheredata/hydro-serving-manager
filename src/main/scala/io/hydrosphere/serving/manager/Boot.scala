package io.hydrosphere.serving.manager

import akka.actor.ActorSystem
import akka.stream.Materializer
import cats.effect._
import cats.implicits._
import com.spotify.docker.client.DefaultDockerClient
import distage.Injector
import io.hydrosphere.serving.manager.api.grpc.GrpcServer
import io.hydrosphere.serving.manager.api.http.HttpServer
import io.hydrosphere.serving.manager.config.ManagerConfiguration
import io.hydrosphere.serving.manager.infrastructure.docker.DockerdClient
import io.hydrosphere.serving.manager.util.{ReflectionUtils, UnsafeLogging}
import izumi.distage.model.plan.GCMode

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object Boot extends IOApp with UnsafeLogging {
  override def run(args: List[String]): IO[ExitCode] =
    IO.suspend {
      implicit val as: ActorSystem              = ActorSystem("manager")
      implicit val mat: Materializer            = Materializer.createMaterializer(as)
      implicit val ec: ExecutionContextExecutor = ExecutionContext.global
      for {
        configuration <- ManagerConfiguration.load[IO]
        _             <- IO(logger.info(s"Config loaded:\n${ReflectionUtils.prettyPrint(configuration)}"))
        dockerClient  <- IO(DefaultDockerClient.fromEnv().readTimeoutMillis(60 * 60 * 1000).build())
        wrappedClient <- DockerdClient.create[IO](dockerClient)
        modules  = App.allModules[IO](configuration, wrappedClient)
        plan     = Injector().plan(modules, GCMode.NoGC)
        resource = Injector().produce(plan)
        _ <- resource.use { locator =>
          locator.get[DBStarter[IO]].init() >>
            locator.get[DBStarter[IO]].checkApplicationGraphs() >>
            locator.get[HttpServer[IO]].start() >>
            locator.get[GrpcServer[IO]].start() >>
            IO.never
        }
      } yield ExitCode.Success
    }
}
