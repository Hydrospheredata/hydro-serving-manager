package io.hydrosphere.serving.manager.it

import io.hydrosphere.serving.manager.infrastructure.docker.IsolatedDockerClient
import io.hydrosphere.serving.manager.util.UnsafeLogging
import org.scalatest.{AsyncFunSpecLike, BeforeAndAfterAll}

trait IsolatedDockerAccessIT extends AsyncFunSpecLike with BeforeAndAfterAll with UnsafeLogging {
  val dockerClient = IsolatedDockerClient.createFromEnv
  logger.info("Initialized IsolatedDockerClient")

  override protected def afterAll(): Unit = {
    logger.info("Cleaning up images and containers.")
    dockerClient.clear()
    super.beforeAll()
  }

  sys.addShutdownHook {
    dockerClient.clear()
  }
}