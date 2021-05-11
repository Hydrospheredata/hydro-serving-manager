package io.hydrosphere.serving.manager.infrastructure.kubernetes

import akka.actor.ActorSystem
import akka.stream.Materializer
import cats.effect._
import cats.implicits._
import io.hydrosphere.serving.manager.config.CloudDriverConfiguration
import io.hydrosphere.serving.manager.domain.clouddriver.CloudInstanceEvent
import io.hydrosphere.serving.manager.domain.clouddriver.CloudInstanceEventAdapterInstances._
import io.hydrosphere.serving.manager.util.AsyncUtil
import skuber._
import skuber.apps.v1.{Deployment, DeploymentList, ReplicaSet}
import skuber.autoscaling.HorizontalPodAutoscaler
import skuber.json.format._
import streamz.converter._

import scala.concurrent.ExecutionContext

trait K8SDeployments[F[_]] {
  def get(name: String): F[Option[Deployment]]

  def create(obj: Deployment): F[Deployment]

  def delete(name: String, gracePeriodSeconds: Int = -1): F[Unit]

  def list: F[List[Deployment]]
}

trait K8SReplicaSets[F[_]] {
  def events(): fs2.Stream[F, CloudInstanceEvent]
}

trait K8SServices[F[_]] {
  def get(name: String): F[Option[Service]]

  def create(obj: Service): F[Service]

  def delete(name: String, gracePeriodSeconds: Int = -1): F[Unit]

  def list: F[List[Service]]

  def events(): fs2.Stream[F, CloudInstanceEvent]
}

trait K8SPods[F[_]] {
  def selectFirstPod(selector: LabelSelector): F[Option[Pod]]

  def logs(name: String, follow: Boolean): fs2.Stream[F, String]
}

trait K8SHorizontalPodAutoscalers[F[_]] {
  def get(name: String): F[Option[HorizontalPodAutoscaler]]

  def create(obj: HorizontalPodAutoscaler): F[HorizontalPodAutoscaler]

  def delete(name: String, gracePeriodSeconds: Int = -1): F[Unit]
}

case class KubernetesClient[F[_]](
    pods: K8SPods[F],
    services: K8SServices[F],
    deployments: K8SDeployments[F],
    hpa: K8SHorizontalPodAutoscalers[F],
    rs: K8SReplicaSets[F]
) {
  def events()(implicit c: Concurrent[F]): fs2.Stream[F, CloudInstanceEvent] =
    rs.events()
}

object KubernetesClient {
  def make[F[_]: Async](
      config: CloudDriverConfiguration.Kubernetes
  )(implicit
      ex: ExecutionContext,
      cs: ContextShift[F],
      actorSystem: ActorSystem,
      materializer: Materializer
  ): KubernetesClient[F] = {
    val k8sConfig  = K8SConfiguration.useProxyAt(s"http://${config.proxyHost}:${config.proxyPort}")
    val underlying = k8sInit(k8sConfig).usingNamespace(config.kubeNamespace)
    fromSkuber[F](underlying)
  }

  def fromSkuber[F[_]](
      underlying: K8SRequestContext
  )(implicit
      F: Async[F],
      cs: ContextShift[F],
      ec: ExecutionContext,
      actorSystem: ActorSystem,
      materializer: Materializer
  ): KubernetesClient[F] =
    KubernetesClient(
      pods = podImpl[F](underlying),
      services = servicesImpl[F](underlying),
      deployments = deploymentImpl[F](underlying),
      hpa = hpaImpl[F](underlying),
      rs = rsImpl[F](underlying)
    )

  def podImpl[F[_]](underlying: K8SRequestContext)(implicit
      F: Async[F],
      cs: ContextShift[F],
      ec: ExecutionContext,
      actorSystem: ActorSystem,
      materializer: Materializer
  ): K8SPods[F] =
    new K8SPods[F] {
      override def selectFirstPod(selector: LabelSelector): F[Option[Pod]] =
        AsyncUtil.futureAsync(underlying.listSelected[PodList](selector)).map(_.headOption)

      override def logs(name: String, follow: Boolean): fs2.Stream[F, String] = {
        val params = Pod.LogQueryParams(follow = Some(follow))
        for {
          akkaStream <-
            fs2.Stream.eval(AsyncUtil.futureAsync(underlying.getPodLogSource(name, params)))
          bytes <- akkaStream.toStream[F]()
        } yield bytes.utf8String
      }
    }

  def servicesImpl[F[_]](
      underlying: K8SRequestContext
  )(implicit
      F: Async[F],
      cs: ContextShift[F],
      ec: ExecutionContext,
      actorSystem: ActorSystem,
      materializer: Materializer
  ): K8SServices[F] =
    new K8SServices[F] {
      override def get(name: String): F[Option[Service]] =
        AsyncUtil.futureAsync(underlying.getOption[Service](name))

      override def create(obj: Service): F[Service] = AsyncUtil.futureAsync(underlying.create(obj))

      override def delete(name: String, gracePeriodSeconds: Int): F[Unit] =
        AsyncUtil.futureAsync(underlying.delete[Service](name, gracePeriodSeconds))

      override def list: F[List[Service]] =
        AsyncUtil.futureAsync(underlying.list[ServiceList]).map(_.toList)

      override def events(): fs2.Stream[F, CloudInstanceEvent] = {
        val rawStream = underlying.watchAll[Service]().map(f => f.map(_.toEvent))
        for {
          akkaStream <- fs2.Stream.eval(AsyncUtil.futureAsync(rawStream))
          evt <-
            akkaStream
              .filter(_.isRight)
              .map { case Right(value) => value }
              .toStream[F]()
        } yield evt
      }
    }

  def deploymentImpl[F[_]](
      underlying: K8SRequestContext
  )(implicit F: Async[F], ec: ExecutionContext): K8SDeployments[F] =
    new K8SDeployments[F] {
      override def get(name: String): F[Option[Deployment]] =
        AsyncUtil.futureAsync(underlying.getOption[Deployment](name))

      override def create(obj: Deployment): F[Deployment] =
        AsyncUtil.futureAsync(underlying.create(obj))

      override def delete(name: String, gracePeriodSeconds: Int): F[Unit] =
        AsyncUtil.futureAsync(underlying.delete[Deployment](name, gracePeriodSeconds))

      override def list: F[List[Deployment]] =
        AsyncUtil.futureAsync(underlying.list[DeploymentList]).map(_.toList)
    }

  def hpaImpl[F[_]](
      underlying: K8SRequestContext
  )(implicit F: Async[F], ec: ExecutionContext): K8SHorizontalPodAutoscalers[F] =
    new K8SHorizontalPodAutoscalers[F] {
      override def get(name: String): F[Option[HorizontalPodAutoscaler]] =
        AsyncUtil.futureAsync(underlying.getOption[HorizontalPodAutoscaler](name))

      override def create(obj: HorizontalPodAutoscaler): F[HorizontalPodAutoscaler] =
        AsyncUtil.futureAsync(underlying.create(obj))

      override def delete(name: String, gracePeriodSeconds: Int): F[Unit] =
        AsyncUtil.futureAsync(underlying.delete[HorizontalPodAutoscaler](name, gracePeriodSeconds))
    }

  def rsImpl[F[_]](
      underlying: K8SRequestContext
  )(implicit
      F: Async[F],
      ec: ExecutionContext,
      cs: ContextShift[F],
      m: Materializer
  ): K8SReplicaSets[F] =
    () => {
      for {
        akkaStream <-
          fs2.Stream.eval(AsyncUtil.futureAsync(underlying.watchAll[ReplicaSet](None, 20000)))
        evt <-
          akkaStream
            .map(_.toEvent)
            .filter(_.isRight)
            .map {
              case Right(value) => value
            }
            .toStream[F]()
      } yield evt
    }
}
