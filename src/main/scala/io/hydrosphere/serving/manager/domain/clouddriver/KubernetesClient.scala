package io.hydrosphere.serving.manager.domain.clouddriver

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import cats.effect._
import cats.implicits._
import io.hydrosphere.serving.manager.config.{CloudDriverConfiguration, DockerRepositoryConfiguration}
import io.hydrosphere.serving.manager.domain.deploy_config.{DeploymentConfiguration, K8sHorizontalPodAutoscalerConfig}
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.util.AsyncUtil
import org.apache.logging.log4j.scala.Logging
import skuber.Container.PullPolicy
import skuber._
import skuber.apps.v1.{Deployment, DeploymentList}
import skuber.autoscaling.{CPUTargetUtilization, HorizontalPodAutoscaler}
import skuber.autoscaling.HorizontalPodAutoscaler.CrossVersionObjectReference
import skuber.json.format._

import scala.concurrent.ExecutionContext
import scala.language.reflectiveCalls

trait KubernetesClient[F[_]] {
  def services: F[List[skuber.Service]]
  def deployments: F[List[Deployment]]
  
  def runDeployment(
    name: String,
    servable: CloudInstance,
    dockerImage: DockerImage,
    config: Option[DeploymentConfiguration]
  ): F[Deployment]

  def createHPA(
    name: String,
    targetName: String,
    targetApiVersion: String,
    targetKind: String,
    config: K8sHorizontalPodAutoscalerConfig
  ): F[HorizontalPodAutoscaler]

  def runService(name: String, servable: CloudInstance): F[skuber.Service]
  
  def removeDeployment(name: String): F[Unit]
  def removeService(name: String): F[Unit]
  def removeHPA(name: String): F[Unit]
  
  def getLogs(podName: String, follow: Boolean): F[Source[String, _]]
  def getPod(name: String): F[Pod]
}

object KubernetesClient {

  def apply[F[_] : Async](config: CloudDriverConfiguration.Kubernetes, dockerRepoConf: DockerRepositoryConfiguration.Remote)(implicit ex: ExecutionContext, actorSystem: ActorSystem, materializer: Materializer): KubernetesClient[F] =
    KubernetesClient[F](
      config,
      dockerRepoConf,
      k8sInit(K8SConfiguration.useProxyAt(s"http://${config.proxyHost}:${config.proxyPort}")).usingNamespace(config.kubeNamespace)
    )

  def apply[F[_] : Async](config: CloudDriverConfiguration.Kubernetes, dockerRepoConf: DockerRepositoryConfiguration.Remote, underlying: K8SRequestContext)(implicit ex: ExecutionContext): KubernetesClient[F] = new KubernetesClient[F] with Logging {
    override def services: F[List[Service]] = {
      AsyncUtil.futureAsync(underlying.list[ServiceList]()).map(_.toList)
    }

    override def deployments: F[List[Deployment]] = {
      AsyncUtil.futureAsync(underlying.list[DeploymentList]).map(_.toList)
    }

    override def runDeployment(name: String, servable: CloudInstance, dockerImage: DockerImage, crc: Option[DeploymentConfiguration]): F[Deployment] = {
      import LabelSelector.dsl._

      val depConf = crc.flatMap(_.deployment)
      val podConf = crc.flatMap(_.pod)
      val containerConf = crc.flatMap(_.container)

      val dockerRepoHost = dockerRepoConf.pullHost.getOrElse(dockerRepoConf.host)
      val image = dockerImage.replaceUser(dockerRepoHost).toTry.get

      var container = Container("model", image.fullName)
        .exposePort(DefaultConstants.DEFAULT_APP_PORT)
        .withImagePullPolicy(PullPolicy.Always)
        .setEnvVar(DefaultConstants.ENV_APP_PORT, DefaultConstants.DEFAULT_APP_PORT.toString)

      val containerReqs = containerConf.flatMap(_.resources)
      containerReqs.flatMap(_.requests).foreach{ req =>
        container = container.addResourceRequest("cpu", req.cpu)
        container = container.addResourceRequest("memory", req.memory)
      }

      containerReqs.flatMap(_.limits).foreach{ req =>
        container = container.addResourceLimit("cpu", req.cpu)
        container = container.addResourceLimit("memory", req.memory)
      }

      var podSpec = Pod.Spec(
        affinity = podConf.flatMap(_.affinity),
        tolerations = podConf.map(_.tolerations).getOrElse(List.empty)
      ).addImagePullSecretRef(config.kubeRegistrySecretName)

      podConf.flatMap(_.nodeSelector).foreach { ns =>
        ns.map(kv => podSpec = podSpec.addNodeSelector(kv))
      }

      val podTemplate = Pod.Template.Spec(
        metadata = ObjectMeta(name = servable.name),
        spec = Some(podSpec)
      )
        .addContainer(container)
        .addLabels(Map(
          CloudDriver.Labels.ServiceName -> name,
          CloudDriver.Labels.ModelVersionId -> servable.modelVersionId.toString
        ))

      val deployment = apps.v1.Deployment(
        metadata = ObjectMeta(
          name = servable.name,
          labels = Map(
            CloudDriver.Labels.ServiceName -> name,
            CloudDriver.Labels.ModelVersionId -> servable.modelVersionId.toString
          ),
        )
      )
        .withReplicas(depConf.flatMap(_.replicaCount).getOrElse(1))
        .withTemplate(podTemplate)
        .withLabelSelector(CloudDriver.Labels.ServiceName is name)

      for {
        deployed <- deployments
        maybeExist = deployed.find(_.metadata.labels.getOrElse(CloudDriver.Labels.ServiceName, "") == name)
        dpl <- maybeExist match {
          case Some(value) => Async[F].pure(value)
          case None => AsyncUtil.futureAsync(underlying.create(deployment))
        }
      } yield dpl
    }

    override def runService(name: String, servable: CloudInstance): F[skuber.Service] = {
      val service = skuber.Service(metadata = ObjectMeta(name = servable.name))
        .withSelector(CloudDriver.Labels.ServiceName -> name)
        .exposeOnPort(skuber.Service.Port("grpc", Protocol.TCP, DefaultConstants.DEFAULT_APP_PORT))
        .addLabels(Map(
          CloudDriver.Labels.ServiceName -> name,
          CloudDriver.Labels.ModelVersionId -> servable.modelVersionId.toString
        ))
      for {
        deployed <- services
        maybeExist = deployed.find(_.metadata.labels.getOrElse(CloudDriver.Labels.ServiceName, "") == name)
        svc <- maybeExist match {
          case Some(value) => Async[F].pure(value)
          case None => AsyncUtil.futureAsync(underlying.create(service))
        }
      } yield svc
    }

    override def createHPA(
      name: String,
      targetName: String,
      targetApiVersion: String,
      targetKind: String,
      config: K8sHorizontalPodAutoscalerConfig
    ): F[HorizontalPodAutoscaler] = {
      val hpaSpec = autoscaling.HorizontalPodAutoscaler.Spec(
        scaleTargetRef = CrossVersionObjectReference(name = targetName, apiVersion = targetApiVersion),
        minReplicas = config.minReplicas,
        maxReplicas = config.maxReplicas,
        cpuUtilization = config.cpuUtilization.map(CPUTargetUtilization.apply)
      )

      val hpa = autoscaling.HorizontalPodAutoscaler(
        metadata = ObjectMeta(
          name = name,
          labels = Map(
            CloudDriver.Labels.ServiceName -> name,
          )
        ),
        spec = hpaSpec
      )
      AsyncUtil.futureAsync(underlying.create(hpa))
    }

    override def removeDeployment(name: String): F[Unit] = for {
      deployed <- deployments
      maybeDeployment = deployed.find(_.metadata.labels.getOrElse(CloudDriver.Labels.ServiceName, "") == name)
      _ <- maybeDeployment match {
        case Some(value) => AsyncUtil.futureAsync(underlying.delete[Deployment](value.metadata.name))
        case None => Async[F].delay(logger.error(s"kube deployment with name `$name` not found"))
      }
    } yield Unit

    override def removeService(name: String): F[Unit] = for {
      deployed <- services
      maybeService = deployed.find(_.metadata.labels.getOrElse(CloudDriver.Labels.ServiceName, "") == name)
      _ <- maybeService match {
        case Some(value) => AsyncUtil.futureAsync(underlying.delete[Service](value.metadata.name))
        case None => Async[F].delay(logger.error(s"kube service with name `$name` not found"))
      }
    } yield Unit

    override def getLogs(podName: String, follow: Boolean): F[Source[String, _]] = {
      AsyncUtil.futureAsync(underlying.getPodLogSource(podName, Pod.LogQueryParams(follow = Some(follow)))).map(_.map(_.utf8String))
    }

    override def getPod(name: String): F[Pod] = {
      import LabelSelector.dsl._
      AsyncUtil.futureAsync(underlying.listSelected[PodList](CloudDriver.Labels.ServiceName is name)).flatMap { pods: PodList =>
        pods.toList match {
          case head :: _ => Async[F].pure(head)
          case Nil => Async[F].raiseError(new RuntimeException(s"There is no running pods for $name"))
        }
      }
    }

    override def removeHPA(name: String): F[Unit] = {
      AsyncUtil.futureAsync(underlying.delete[HorizontalPodAutoscaler](name))
    }
  }

}