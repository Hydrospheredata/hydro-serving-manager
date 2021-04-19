package io.hydrosphere.serving.manager.domain.servable

import com.spotify.docker.client.messages.{Event => DockerEvent}
import skuber.api.client.{EventType, WatchEvent}
import skuber.apps.v1.ReplicaSet
import cats.implicits._
import skuber.api.client.EventType.EventType
import skuber.{ObjectResource, Service}

sealed trait CloudInstanceEvent {
  def instanceName: String
}

final case class Starting(instanceName: String, warning: Option[String] = None) extends CloudInstanceEvent

final case class Ready(instanceName: String, warning: Option[String] = None) extends CloudInstanceEvent

final case class NotAvailable(instanceName: String, message: String) extends CloudInstanceEvent

final case class NotServing(instanceName: String, message: String) extends CloudInstanceEvent

final case class Available(instanceName: String) extends CloudInstanceEvent

sealed trait CloudInstanceEventAdapterError extends Serializable

final case object MissingLabelError extends CloudInstanceEventAdapterError

final case object UnhandledEvent extends CloudInstanceEventAdapterError

final case class MissingField(message: String) extends CloudInstanceEventAdapterError


trait CloudInstanceAdapter[A] {
  def toEvent(value: A): Either[CloudInstanceEventAdapterError, CloudInstanceEvent]
}

object CloudInstanceEvent {
  type ErrorOr[A] = Either[CloudInstanceEventAdapterError, A]

  implicit class Ops[A](value: A) {
    def toEvent(implicit adapter: CloudInstanceAdapter[A]): ErrorOr[CloudInstanceEvent] = adapter.toEvent(value)
  }

  implicit val dockerEventsAdapter: CloudInstanceAdapter[DockerEvent] = (event: DockerEvent) => {
    val attributes = event.actor().attributes().some;
    val name = attributes.map(_.get("HS_INSTANCE_NAME"))
    val action = event.action().some

    (name, action) match {
      case (None, _) => MissingLabelError.asLeft
      case (_, None) => MissingField("Couldn't get attribute field from Docker's event").asLeft
      case (Some(name), Some(action)) =>
        action match {
          case "create" => Starting(name).asRight
          case "start" => Ready(name).asRight
          case "stop" => NotServing(name, "Was internally stopped").asRight
        }
    }
  }

  implicit val rsAdapter: CloudInstanceAdapter[WatchEvent[ReplicaSet]] = (value: WatchEvent[ReplicaSet]) => {
    val replicaSet = value._object;

    val currentReplicasOrError: ErrorOr[Int] = replicaSet.status.map(_.replicas) match {
      case Some(replicas) => replicas.asRight
      case None => MissingField("Current replicas aren't found").asLeft
    }
    val desiredReplicasOrError: ErrorOr[Int] = replicaSet.spec.flatMap(_.replicas) match {
      case Some(replicas) => replicas.asRight
      case None => MissingField("Desired replicas aren't found").asLeft
    }
    val instanceNameOrError: ErrorOr[String] = replicaSet.metadata.labels.get("HS_INSTANCE_NAME") match {
      case Some(instanceName) => instanceName.asRight
      case None => MissingLabelError.asLeft
    }

    for {
      instanceName <- instanceNameOrError
      desiredReplicas <- desiredReplicasOrError
      currentReplicas <- currentReplicasOrError
    } yield {
      if (currentReplicas == 0)
        NotServing(instanceName, "All pods aren't available")
      else if (currentReplicas < desiredReplicas)
        Ready(instanceName, s"${currentReplicas} from ${desiredReplicas} are available".some)
      else
        Ready(instanceName)
    }
  }

  implicit val serviceAdapter: CloudInstanceAdapter[WatchEvent[Service]] = (value: WatchEvent[Service]) => {
    val service = value._object;

    val instanceNameOrError: ErrorOr[String] = service.metadata.labels.get("HS_INSTANCE_NAME") match {
      case Some(instanceName) => instanceName.asRight
      case None => MissingLabelError.asLeft
    }

    for {
      instanceName <- instanceNameOrError
      event <- value._type match {
        case EventType.DELETED => NotAvailable(instanceName, "Service has been deleted").asRight
        case EventType.ERROR => NotAvailable(instanceName, "Service failed with error").asRight
        case EventType.ADDED => Available(instanceName).asRight
        case _ => UnhandledEvent.asLeft
      }
    } yield event
  }
}