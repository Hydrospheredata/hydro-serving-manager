package io.hydrosphere.serving.manager.domain.clouddriver.k8s

import cats.implicits.{catsSyntaxEitherId, catsSyntaxOptionId}
import io.hydrosphere.serving.manager.domain.clouddriver.{
  CloudDriver,
  CloudInstanceEventAdapter,
  CloudInstanceEventAdapterError,
  MissingField,
  MissingLabel,
  UnhandledEvent
}
import skuber.{ObjectResource, Service}
import skuber.api.client.{EventType, WatchEvent}
import skuber.apps.v1.ReplicaSet

object K8sCloudInstanceEventAdapter {
  type ErrorOr[A] = Either[CloudInstanceEventAdapterError, A]

  private def getInstanceName(resource: ObjectResource): ErrorOr[String] =
    resource.metadata.labels.get(CloudDriver.Labels.ServiceName) match {
      case Some(instanceName) => instanceName.asRight
      case None               => MissingLabel.asLeft
    }

  implicit val rsAdapter: CloudInstanceEventAdapter[WatchEvent[ReplicaSet]] =
    (value: WatchEvent[ReplicaSet]) => {
      val replicaSet = value._object

      val currentReplicasOrError: ErrorOr[Int] = replicaSet.status.map(_.replicas) match {
        case Some(replicas) => replicas.asRight
        case None           => MissingField("Current replicas aren't found").asLeft
      }
      val desiredReplicasOrError: ErrorOr[Int] = replicaSet.spec.flatMap(_.replicas) match {
        case Some(replicas) => replicas.asRight
        case None           => MissingField("Desired replicas aren't found").asLeft
      }
      val instanceNameOrError: ErrorOr[String] = getInstanceName(replicaSet)

      for {
        name            <- instanceNameOrError
        desiredReplicas <- desiredReplicasOrError
        currentReplicas <- currentReplicasOrError
      } yield
        if (currentReplicas == 0)
          ReplicaSetIsFailed(name, "All pods aren't available")
        else if (currentReplicas < desiredReplicas)
          ReplicaSetIsOk(
            name,
            s"$currentReplicas from $desiredReplicas are available".some
          )
        else
          ReplicaSetIsOk(name)
    }

  implicit val serviceAdapter: CloudInstanceEventAdapter[WatchEvent[Service]] =
    (value: WatchEvent[Service]) => {
      val service = value._object

      val instanceNameOrError: ErrorOr[String] = getInstanceName(service)

      for {
        name <- instanceNameOrError
        event <- value._type match {
          case EventType.DELETED => ServiceIsUnavailable(name, "Service has been deleted").asRight
          case EventType.ERROR   => ServiceIsUnavailable(name, "Service failed with an error").asRight
          case EventType.ADDED   => ServiceIsAvailable(name).asRight
          case _                 => UnhandledEvent.asLeft
        }
      } yield event
    }
}
