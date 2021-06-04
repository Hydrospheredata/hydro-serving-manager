package io.hydrosphere.serving.manager.domain.clouddriver

import com.spotify.docker.client.messages.Event
import k8s.K8sCloudInstanceEventAdapter._
import docker.DockerCloudInstanceEventAdapter
import io.hydrosphere.serving.manager.domain.clouddriver.k8s.K8sCloudInstanceEventAdapter
import skuber.Service
import skuber.api.client
import skuber.apps.v1.ReplicaSet

object CloudInstanceEventAdapterInstances {
  type ErrorOr[A] = Either[CloudInstanceEventAdapterError, A]

  implicit val dockerAdapter: CloudInstanceEventAdapter[Event] =
    DockerCloudInstanceEventAdapter.dockerEventsAdapter

  implicit val serviceAdapter: CloudInstanceEventAdapter[client.WatchEvent[Service]] =
    K8sCloudInstanceEventAdapter.serviceAdapter
  implicit val rsAdapter: CloudInstanceEventAdapter[client.WatchEvent[ReplicaSet]] =
    K8sCloudInstanceEventAdapter.rsAdapter

  implicit class Ops[A](value: A) {
    def toEvent(implicit adapter: CloudInstanceEventAdapter[A]): ErrorOr[CloudInstanceEvent] =
      adapter.toEvent(value)
  }
}
