package io.hydrosphere.serving.manager.domain.clouddriver.docker

import com.spotify.docker.client.messages.Event
import io.hydrosphere.serving.manager.domain.clouddriver.{
  CloudDriver,
  CloudInstanceEventAdapter,
  MissingField,
  MissingLabel
}
import cats.implicits._

object DockerCloudInstanceEventAdapter {
  implicit val dockerEventsAdapter: CloudInstanceEventAdapter[Event] = (event: Event) => {
    val attributes = event.actor().attributes().some;
    val name       = attributes.map(_.get(CloudDriver.Labels.ServiceName))
    val action     = event.action().some

    (name, action) match {
      case (None, _) => MissingLabel.asLeft
      case (_, None) => MissingField("Couldn't get attribute field from Docker's event").asLeft
      case (Some(name), Some(action)) =>
        action match {
          case "create"                   => Create(name).asRight
          case "start"                    => Start(name).asRight
          case "health_status: healthy"   => Healthy(name).asRight
          case "health_status: unhealthy" => Unhealthy(name).asRight
          case "stop"                     => Stop(name, "was internally stopped").asRight
        }
    }
  }
}
