package io.hydrosphere.serving.manager.domain.clouddriver

import cats.MonadError
import cats.data.OptionT
import cats.implicits._
import io.hydrosphere.serving.manager.config.{CloudDriverConfiguration, DockerRepositoryConfiguration}
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.deploy_config._
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.infrastructure.kubernetes.KubernetesClient
import skuber.Container.PullPolicy
import skuber.Resource.Quantity
import skuber.Service.Port
import skuber.apps.v1.Deployment
import skuber.autoscaling.HorizontalPodAutoscaler.CrossVersionObjectReference
import skuber.autoscaling.{CPUTargetUtilization, HorizontalPodAutoscaler}
import skuber.{Container, EnvVar, LocalObjectReference, ObjectMeta, Pod, Protocol, Service, autoscaling}
import scala.language.reflectiveCalls

import scala.util.Try

class KubernetesDriver[F[_]](
  client: KubernetesClient[F],
  k8sConfig: CloudDriverConfiguration.Kubernetes,
  dockerRepoConf: DockerRepositoryConfiguration.Remote
)(implicit F: MonadError[F, Throwable]) extends CloudDriver[F] {
  private def kubeSvc2Servable(svc: skuber.Service) = for {
    modelVersionId <- Try(svc.metadata.labels(CloudDriver.Labels.ModelVersionId).toLong).toEither
    serviceName <- Try(svc.metadata.labels(CloudDriver.Labels.ServiceName)).toEither
    host = s"dns:///${svc.metadata.name}.${svc.metadata.namespace}.svc.cluster.local"
    port <- svc.spec
      .toList
      .flatMap(_.ports)
      .collectFirst { case Port("grpc", _, port, _, _) => port }
      .toRight(new IllegalArgumentException(s"SVC doesn't expose grpc port: $svc"))
  } yield CloudInstance(
    modelVersionId,
    serviceName,
    CloudInstance.Status.Running(host, port)
  )

  override def instances: F[List[CloudInstance]] = client.services.list.map(_.map(kubeSvc2Servable).collect { case Right(v) => v })

  override def instance(name: String): F[Option[CloudInstance]] = instances.map(_.find(_.name == name))

  override def run(name: String, modelVersionId: Long, image: DockerImage, config: Option[DeploymentConfiguration] = None): F[CloudInstance] = {
    val depTemplate = KubernetesDriver.prepareDeployment(
      name = name,
      modelVersionId = modelVersionId,
      dockerImage = image,
      dockerRepoHost = dockerRepoConf.pullHost.getOrElse(dockerRepoConf.host),
      kubeRegistrySecretName = k8sConfig.kubeRegistrySecretName,
      crc = config
    )

    val hpaTemplate = config.flatMap(_.hpa).map(KubernetesDriver.prepareHPA(name, depTemplate, _))
    val serviceTemplate = KubernetesDriver.prepareService(name, modelVersionId)
    for {
      _ <- client.deployments.create(depTemplate)
      _ <- hpaTemplate.traverse(client.hpa.create)
      service <- client.services.create(serviceTemplate)
      maybeServable = kubeSvc2Servable(service)
      newServable <- maybeServable match {
        case Right(value) => F.pure(value)
        case Left(error) => F.raiseError[CloudInstance](new RuntimeException(s"Cannot create Servable from kube Service $service Reason: $error"))
      }
    } yield newServable
  }

  override def remove(name: String): F[Unit] = {
    val removeHpa = client.hpa.get(name)
      .flatMap(_.traverse(_ => client.hpa.delete(name)).void)

    client.services.delete(name) >>
      removeHpa >>
      client.deployments.delete(name)
  }

  override def getByVersionId(modelVersionId: Long): F[Option[CloudInstance]] = {
    instances.map(_.find(_.modelVersionId == modelVersionId))
  }

  override def getLogs(name: String, follow: Boolean): fs2.Stream[F, String] = {
    import skuber.LabelSelector.dsl._
    val maybePod = OptionT(client.pods.selectFirstPod(CloudDriver.Labels.ServiceName is name))
      .getOrElseF(F.raiseError(DomainError.notFound(s"Can't find pod with ServiceName=$name")))
    fs2.Stream.eval(maybePod).flatMap(pod => client.pods.logs(pod.metadata.name, follow))
  }

}

object KubernetesDriver {
  def map2ResourceList(x: Requirement): Map[String, Quantity] = {
    x.map { case (k, v) => k -> Quantity(v) }
  }

  def prepareDeployment(
    name: String,
    modelVersionId: Long,
    dockerImage: DockerImage,
    dockerRepoHost: String,
    kubeRegistrySecretName: String,
    crc: Option[DeploymentConfiguration] = None
  ): Deployment = {
    import skuber.LabelSelector.dsl._

    val depConf = crc.flatMap(_.deployment)
    val podConf = crc.flatMap(_.pod)
    val containerConf = crc.flatMap(_.container)

    val image = dockerImage.replaceUser(dockerRepoHost).toTry.get

    val depEnvs = containerConf.flatMap(_.env).getOrElse(Map.empty) ++
      Map(DefaultConstants.ENV_APP_PORT -> DefaultConstants.DEFAULT_APP_PORT.toString)

    val containerEnvs = depEnvs.map {
      case (k, v) => EnvVar(k, EnvVar.strToValue(v))
    }.toList

    val resources = containerConf.flatMap(_.resources).map { r =>
      skuber.Resource.Requirements(
        limits = r.limits.map(map2ResourceList).getOrElse(Map.empty),
        requests = r.requests.map(map2ResourceList).getOrElse(Map.empty)
      )
    }

    val container = Container(
      name = "model",
      image = image.fullName,
      env = containerEnvs,
      ports = List(Container.Port(DefaultConstants.DEFAULT_APP_PORT)),
      imagePullPolicy = PullPolicy.Always,
      resources = resources
    )

    val podTemplate = Pod.Template.Spec(
      metadata = ObjectMeta(
        name = name,
        labels = Map(
          CloudDriver.Labels.ServiceName -> name,
          CloudDriver.Labels.ModelVersionId -> modelVersionId.toString
        )
      ),
      spec = Pod.Spec(
        affinity = podConf.flatMap(_.affinity),
        tolerations = podConf.map(_.tolerations).getOrElse(List.empty),
        imagePullSecrets = List(LocalObjectReference(kubeRegistrySecretName)),
        nodeSelector = podConf.flatMap(_.nodeSelector).getOrElse(Map.empty),
        containers = List(container)
      ).some
    )

    Deployment(
      metadata = ObjectMeta(
        name = name,
        labels = Map(
          CloudDriver.Labels.ServiceName -> name,
          CloudDriver.Labels.ModelVersionId -> modelVersionId.toString
        ),
      ),
      spec = Deployment.Spec(
        replicas = depConf.flatMap(_.replicaCount).orElse(1.some),
        selector = CloudDriver.Labels.ServiceName is name,
        template = podTemplate
      ).some
    )
  }

  def prepareHPA(name: String, deployment: Deployment, config: K8sHorizontalPodAutoscalerConfig): HorizontalPodAutoscaler = {
    val hpaSpec = autoscaling.HorizontalPodAutoscaler.Spec(
      scaleTargetRef = CrossVersionObjectReference(
        name = deployment.metadata.name,
        apiVersion = deployment.apiVersion,
        kind = deployment.kind
      ),
      minReplicas = config.minReplicas,
      maxReplicas = config.maxReplicas,
      cpuUtilization = config.cpuUtilization.map(CPUTargetUtilization.apply)
    )

    autoscaling.HorizontalPodAutoscaler(
      metadata = ObjectMeta(
        name = name,
        labels = Map(
          CloudDriver.Labels.ServiceName -> name,
        )
      ),
      spec = hpaSpec
    )
  }

  def prepareService(name: String, modelVersionId: Long): Service = {
    skuber.Service(metadata = ObjectMeta(name = name))
      .withSelector(CloudDriver.Labels.ServiceName -> name)
      .exposeOnPort(skuber.Service.Port("grpc", Protocol.TCP, DefaultConstants.DEFAULT_APP_PORT))
      .addLabels(Map(
        CloudDriver.Labels.ServiceName -> name,
        CloudDriver.Labels.ModelVersionId -> modelVersionId.toString
      ))
  }
}