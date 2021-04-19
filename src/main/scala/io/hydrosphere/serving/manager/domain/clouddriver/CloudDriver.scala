package io.hydrosphere.serving.manager.domain.clouddriver

import akka.actor.ActorSystem
import akka.stream.Materializer
import cats.effect._
import io.hydrosphere.serving.manager.config.{CloudDriverConfiguration, DockerRepositoryConfiguration}
import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfiguration
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.servable.{CloudInstanceEventAdapterError, CloudInstanceEvent}
import io.hydrosphere.serving.manager.infrastructure.docker.DockerdClient
import io.hydrosphere.serving.manager.infrastructure.kubernetes.KubernetesClient

import scala.concurrent.ExecutionContext

object CloudInstance {

  sealed trait Status

  object Status {

    case object Starting extends Status

    final case class Running(host: String, port: Int) extends Status

    case object Stopped extends Status

  }

}

case class CloudInstance(
  modelVersionId: Long,
  name: String,
  status: CloudInstance.Status
)

trait CloudDriver[F[_]] {

  def instances: F[List[CloudInstance]]

  def instance(name: String): F[Option[CloudInstance]]

  def run(name: String, modelVersionId: Long, image: DockerImage, config: Option[DeploymentConfiguration]): F[CloudInstance]

  def remove(name: String): F[Unit]

  def getByVersionId(modelVersionId: Long): F[Option[CloudInstance]]

  def getLogs(name: String, follow: Boolean): fs2.Stream[F, String]

  def getEvents: fs2.Stream[F, CloudInstanceEvent]
}

object CloudDriver {

  object Labels {
    val ServiceName    = "HS_INSTANCE_NAME"
    val ModelVersionId = "HS_INSTANCE_MV_ID"
    val ServiceId      = "HS_INSTANCE_ID"
  }

  def fromConfig[F[_]](
      dockerdClient: DockerdClient[F],
      config: CloudDriverConfiguration,
      dockerRepoConf: DockerRepositoryConfiguration
  )(implicit
      F: Async[F],
      cs: ContextShift[F],
      ex: ExecutionContext,
      actorSystem: ActorSystem,
      materializer: Materializer,
      c: Concurrent[F]
  ): CloudDriver[F] =
    config match {
      case dockerConf: CloudDriverConfiguration.Docker =>
        new DockerDriver[F](dockerdClient, dockerConf)
      case kubeConf: CloudDriverConfiguration.Kubernetes =>
        dockerRepoConf match {
          case drc: DockerRepositoryConfiguration.Remote =>
            val client = KubernetesClient.make[F](kubeConf)
            new KubernetesDriver[F](client, kubeConf, drc)
          case _ =>
            throw new Exception(
              s"Docker Repository must be remote for using kubernetes cloud driver"
            )
        }
      case x =>
        throw new Exception(s"Not implemented for $x")
    }
}
