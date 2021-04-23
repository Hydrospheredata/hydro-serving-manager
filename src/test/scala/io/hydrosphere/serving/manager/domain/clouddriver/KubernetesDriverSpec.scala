package io.hydrosphere.serving.manager.domain.clouddriver

import cats.effect.IO
import cats.implicits._
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.config.{
  CloudDriverConfiguration,
  DockerRepositoryConfiguration
}
import io.hydrosphere.serving.manager.domain.deploy_config._
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.infrastructure.kubernetes._
import org.mockito.Mockito
import skuber.apps.v1.Deployment
import skuber.autoscaling.HorizontalPodAutoscaler
import skuber.autoscaling.HorizontalPodAutoscaler.CrossVersionObjectReference
import skuber.{ObjectMeta, Protocol, Service}
import skuber.json.format._

import scala.reflect.ClassTag

class KubernetesDriverSpec extends GenericUnitTest {
  private val cdConfig = CloudDriverConfiguration.Kubernetes("asd", 123, "test", "secret", None)
  private val cdDrc    = DockerRepositoryConfiguration.Remote("host", None, None, None, None)

  def getOrMock[T](opt: Option[T])(implicit ct: ClassTag[T]) =
    opt.getOrElse(Mockito.mock(ct.runtimeClass.asInstanceOf[Class[T]]))

  protected def mockClient[F[_]](
      pod: Option[K8SPods[F]] = None,
      deployments: Option[K8SDeployments[F]] = None,
      services: Option[K8SServices[F]] = None,
      hpa: Option[K8SHorizontalPodAutoscalers[F]] = None,
      rs: Option[K8SReplicaSets[F]] = None
  ) =
    KubernetesClient[F](
      pods = getOrMock(pod),
      services = getOrMock(services),
      deployments = getOrMock(deployments),
      hpa = getOrMock(hpa),
      rs = getOrMock(rs)
    )

  describe("KubernetesDriver") {
    val name           = "asd"
    val modelVersionId = 1
    val image          = DockerImage("yep/image", "tag")

    it("should create a Deployment and a Service with no DepConf for a Servable") {
      val config = DeploymentConfiguration.empty

      // TODO set specific arguments
      val deployments = mock[K8SDeployments[IO]]
      val dep1 = KubernetesDriver.prepareDeployment(
        name = "asd",
        modelVersionId = 1,
        dockerImage = image,
        dockerRepoHost = "host",
        kubeRegistrySecretName = "secret",
        crc = config
      )

      when(deployments.create(dep1)).thenReturn(dep1.pure[IO])

      val services = mock[K8SServices[IO]]
      val service1 = skuber
        .Service(metadata = ObjectMeta(name = "asd"))
        .withClusterIP("None")
        .withSelector(CloudDriver.Labels.ServiceName -> "asd")
        .exposeOnPort(skuber.Service.Port("grpc", Protocol.TCP, DefaultConstants.DEFAULT_APP_PORT))
        .addLabels(
          Map(
            CloudDriver.Labels.ServiceName    -> "asd",
            CloudDriver.Labels.ModelVersionId -> "1"
          )
        )

      val service1Res = skuber
        .Service(metadata = ObjectMeta(name = "asd"))
        .withSelector(CloudDriver.Labels.ServiceName -> "asd")
        .exposeOnPort(skuber.Service.Port("grpc", Protocol.TCP, DefaultConstants.DEFAULT_APP_PORT))
        .addLabels(
          Map(
            CloudDriver.Labels.ServiceName    -> "asd",
            CloudDriver.Labels.ModelVersionId -> "1"
          )
        )
        .withClusterIP("None")

      when(services.create(service1)).thenReturn(service1Res.pure[IO])

      val client = mockClient(
        deployments = deployments.some,
        services = services.some
      )
      val driver = new KubernetesDriver[IO](client, cdConfig, cdDrc)

      val result = driver
        .run(
          name = name,
          modelVersionId = modelVersionId,
          image = image,
          config = config
        )
        .unsafeRunSync()
      assert(result.status === CloudInstance.Status.Running("dns:///asd..svc.cluster.local", 9091))
    }

    it("should create a Deployment, a Service, and a HPA for a Servable") {
      val hpaConfig = K8sHorizontalPodAutoscalerConfig(
        minReplicas = 2.some,
        maxReplicas = 3,
        cpuUtilization = 80.some
      )
      val config = DeploymentConfiguration(
        name = "test",
        hpa = hpaConfig.some,
        container = None,
        pod = None,
        deployment = None
      )

      val deps = mock[K8SDeployments[IO]]
      when(deps.create(any)).thenReturn(
        Deployment(name)
          .pure[IO]
      )
      val svc = mock[K8SServices[IO]]
      when(svc.create(any)).thenReturn(
        Service(name)
          .addLabel(CloudDriver.Labels.ModelVersionId -> modelVersionId.toString)
          .addLabel(CloudDriver.Labels.ServiceName -> name)
          .withClusterIP("127.0.0.1")
          .exposeOnPort(Service.Port(name = "grpc", port = 9090))
          .pure[IO]
      )
      val hpa  = mock[K8SHorizontalPodAutoscalers[IO]]
      val hpa1 = KubernetesDriver.prepareHPA(name, Deployment(name), hpaConfig)
      when(hpa.create(hpa1)).thenReturn(hpa1.pure[IO])
      val client = mockClient(deployments = deps.some, services = svc.some, hpa = hpa.some)
      val driver = new KubernetesDriver[IO](client, cdConfig, cdDrc)

      val result = driver
        .run(
          name = name,
          modelVersionId = modelVersionId,
          image = image,
          config = config
        )
        .unsafeRunSync()
      assert(result.status === CloudInstance.Status.Running("dns:///asd..svc.cluster.local", 9090))
    }

    it("should delete a Deployment and a Service") {
      val deps = mock[K8SDeployments[IO]]
      when(deps.delete(name)).thenReturn(IO.unit)
      val svc = mock[K8SServices[IO]]
      when(svc.delete(name)).thenReturn(IO.unit)
      val hpa = mock[K8SHorizontalPodAutoscalers[IO]]
      when(hpa.get(name)).thenReturn(None.pure[IO])

      val client = mockClient(deployments = deps.some, services = svc.some, hpa = hpa.some)
      val driver = new KubernetesDriver[IO](client, cdConfig, cdDrc)

      driver.remove(name).unsafeRunSync()
      succeed
    }

    it("should delete a Deployment, a Service, and a HPA") {
      val hpaTemplate = HorizontalPodAutoscaler(
        metadata = skuber.ObjectMeta(),
        spec = HorizontalPodAutoscaler.Spec(
          scaleTargetRef = CrossVersionObjectReference(
            apiVersion = "apps/v1",
            kind = "Deployment",
            name = name
          )
        )
      )

      val deps = mock[K8SDeployments[IO]]
      when(deps.delete(name)).thenReturn(IO.unit)

      val svc = mock[K8SServices[IO]]
      when(svc.delete(name)).thenReturn(IO.unit)

      val hpa = mock[K8SHorizontalPodAutoscalers[IO]]
      when(hpa.get(name)).thenReturn(hpaTemplate.some.pure[IO])
      when(hpa.delete(name)).thenReturn(IO.unit)

      val client = mockClient(deployments = deps.some, services = svc.some, hpa = hpa.some)
      val driver = new KubernetesDriver[IO](client, cdConfig, cdDrc)

      driver.remove(name).unsafeRunSync()
      succeed
    }
  }
}
