package io.hydrosphere.serving.manager.domain.clouddriver

import akka.stream.scaladsl.Source
import cats.MonadError
import cats.effect.Async
import cats.implicits._
import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfiguration
import io.hydrosphere.serving.manager.domain.image.DockerImage
import skuber.Service.Port

import scala.util.Try

class KubernetesDriver[F[_]](client: KubernetesClient[F])(implicit F: MonadError[F, Throwable]) extends CloudDriver[F] {
  private def kubeSvc2Servable(svc: skuber.Service) = for {
    modelVersionId <- Try(svc.metadata.labels(CloudDriver.Labels.ModelVersionId).toLong).toEither
    serviceName <- Try(svc.metadata.labels(CloudDriver.Labels.ServiceName)).toEither
    host <- svc.spec
      .map(_.clusterIP)
      .toRight(new IllegalArgumentException(s"SVC doesn't have ClusterIP: ${svc}"))
    port <- svc.spec
      .toList
      .flatMap(_.ports)
      .collectFirst{case Port("grpc", _, port, _, _) => port}
      .toRight(new IllegalArgumentException(s"SVC doesn't expose grpc port: ${svc}"))
  } yield CloudInstance(
    modelVersionId,
    serviceName,
    CloudInstance.Status.Running(host, port)
  )

  override def instances: F[List[CloudInstance]] = client.services.map(_.map(kubeSvc2Servable).collect { case Right(v) => v })

  override def instance(name: String): F[Option[CloudInstance]] = instances.map(_.find(_.name == name))


  override def run(name: String, modelVersionId: Long, image: DockerImage, config: Option[DeploymentConfiguration] = None): F[CloudInstance] = {
    val servable = CloudInstance(modelVersionId, name, CloudInstance.Status.Starting)
    for {
      dep <- client.runDeployment(name, servable, image, config)
      _ <- config.flatMap(_.hpa).traverse(client.createHPA(name, dep.metadata.name, dep.apiVersion, dep.kind, _))
      service <- client.runService(name, servable)
      maybeServable = kubeSvc2Servable(service)
      newServable <- maybeServable match {
        case Right(value) => F.pure(value)
        case Left(error) => F.raiseError[CloudInstance](new RuntimeException(s"Cannot create Servable from kube Service $service Reason: $error"))
      }
    } yield newServable
  }

  override def remove(name: String): F[Unit] = {
    val removeHpa = client.getHPA(name)
      .flatMap(_.traverse(_ => client.removeHPA(name)).void)

    client.removeService(name) *>
      removeHpa *>
      client.removeDeployment(name)
  }

  override def getByVersionId(modelVersionId: Long): F[Option[CloudInstance]] = {
    instances.map(_.find(_.modelVersionId == modelVersionId))
  }

  override def getLogs(name: String, follow: Boolean): F[Source[String, _]] = for {
    pod <- client.getPod(name)
    logs <- client.getLogs(pod.metadata.name, follow)
  } yield logs
}