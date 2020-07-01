package io.hydrosphere.serving.manager.domain.clouddriver

import akka.stream.scaladsl.Source
import cats.effect.Async
import cats.implicits._
import io.hydrosphere.serving.manager.domain.host_selector.HostSelector
import io.hydrosphere.serving.manager.domain.image.DockerImage

import scala.util.Try

class KubernetesDriver[F[_]: Async](client: KubernetesClient[F]) extends CloudDriver[F] {
  private def kubeSvc2Servable(svc: skuber.Service): Option[CloudInstance] = for {
    modelVersionId <- Try(svc.metadata.labels(CloudDriver.Labels.ModelVersionId).toLong).toOption
    serviceName <- Try(svc.metadata.labels(CloudDriver.Labels.ServiceName)).toOption
    host <- svc.spec.map(_.clusterIP)
    port <- svc.spec.flatMap(_.ports.find(_.name == "grpc")).map(_.port)
  } yield CloudInstance(
    modelVersionId,
    serviceName,
    CloudInstance.Status.Running(host, port)
  )

  override def instances: F[List[CloudInstance]] = client.services.map(_.map(kubeSvc2Servable).collect { case Some(v) => v })

  override def instance(name: String): F[Option[CloudInstance]] = instances.map(_.find(_.name == name))


  override def run(name: String, modelVersionId: Long, image: DockerImage, config: Option[CloudResourceConfiguration] = None): F[CloudInstance] = {
    val servable = CloudInstance(modelVersionId, name, CloudInstance.Status.Starting)
    for {
      _ <- client.runDeployment(name, servable, image, config)
      service <- client.runService(name, servable)
      maybeServable = kubeSvc2Servable(service)
      newServable <- maybeServable match {
        case Some(value) => Async[F].pure(value)
        case None => Async[F].raiseError[CloudInstance](new RuntimeException(s"Cannot create Servable from kube Service $service"))
      }
    } yield newServable
  }

  override def remove(name: String): F[Unit] = client.removeService(name) *> client.removeDeployment(name)

  override def getByVersionId(modelVersionId: Long): F[Option[CloudInstance]] = {
    instances.map(_.find(_.modelVersionId == modelVersionId))
  }

  override def getLogs(name: String, follow: Boolean): F[Source[String, _]] = for {
    pod <- client.getPod(name)
    logs <- client.getLogs(pod.metadata.name, follow)
  } yield logs
}