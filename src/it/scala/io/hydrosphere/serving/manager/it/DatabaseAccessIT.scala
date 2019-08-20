package io.hydrosphere.serving.manager.it

import cats.effect.IO
import com.spotify.docker.client.messages.{ContainerConfig, HostConfig, PortBinding}
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.config.HikariConfiguration
import io.hydrosphere.serving.manager.infrastructure.db.Database

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

trait DatabaseAccessIT extends IsolatedDockerAccessIT {

  def config = ContainerConfig.builder()
    .image("postgres:9.6-alpine")
    .hostConfig(
      HostConfig.builder().portBindings(
        Map(
          "5432/tcp" -> List(
            PortBinding.of("", 5432)
          ).asJava
        ).asJava
      ).build()
    )
    .exposedPorts("5432/tcp")
    .env(
      "POSTGRES_DB=docker",
      "POSTGRES_PASSWORD=docker",
      "POSTGRES_USER=docker"
    )
    .build()

  def container = dockerClient.createContainer(config)
  logger.info("Created db container")

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    dockerClient.startContainer(container.id())
    logger.info("Starting db container")
  }
}
