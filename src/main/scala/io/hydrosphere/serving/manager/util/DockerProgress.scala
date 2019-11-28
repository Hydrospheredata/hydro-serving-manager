package io.hydrosphere.serving.manager.util

import com.spotify.docker.client.ProgressHandler
import com.spotify.docker.client.messages.ProgressMessage
import org.apache.logging.log4j.scala.Logging

object DockerProgress extends Logging {
  def extractMessage(message: ProgressMessage): Option[String] = {
    Option(message.error())
          .orElse(Option(message.stream()))
          .orElse(Option(message.status()))
  }

  def makeLogger(logger: (String => Unit)): ProgressHandler = {
    new ProgressHandler{
      override def progress(message: ProgressMessage): Unit = {
        val maybeMsg = extractMessage(message)
        maybeMsg.foreach(logger)
      }
    }
  }
}