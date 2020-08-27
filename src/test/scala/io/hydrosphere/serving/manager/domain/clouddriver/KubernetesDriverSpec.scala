package io.hydrosphere.serving.manager.domain.clouddriver

import cats.effect.IO
import cats.implicits._
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.deploy_config._
import io.hydrosphere.serving.manager.domain.image.DockerImage
import skuber.Service
import skuber.apps.v1.Deployment
import skuber.autoscaling.HorizontalPodAutoscaler
import skuber.autoscaling.HorizontalPodAutoscaler.CrossVersionObjectReference

class KubernetesDriverSpec extends GenericUnitTest {
  describe("KubernetesDriver") {
    it("should create a Deployment and a Service with no DepConf for a Servable") {
      val name = "asd"
      val modelVersionId = 1
      val image = DockerImage("image", "tag")
      val config = None

      val client = mock[KubernetesClient[IO]]
      val cloudInstance = CloudInstance(
        modelVersionId = modelVersionId,
        name = name,
        status = CloudInstance.Status.Starting
      )
      when(client.runDeployment(name, cloudInstance, image, config)).thenReturn(Deployment(name).pure[IO])
      when(client.runService(name, cloudInstance)).thenReturn(
        Service(name)
          .addLabel(CloudDriver.Labels.ModelVersionId -> modelVersionId.toString)
          .addLabel(CloudDriver.Labels.ServiceName -> name)
          .withClusterIP("127.0.0.1")
          .exposeOnPort(Service.Port(name = "grpc", port = 9090))
          .pure[IO]
      )

      val driver = new KubernetesDriver[IO](client)

      val result = driver.run(
        name = name,
        modelVersionId = modelVersionId,
        image = image,
        config = config
      ).unsafeRunSync()
      assert(result.status === CloudInstance.Status.Running("127.0.0.1", 9090))
    }

    it("should create a Deployment, a Service, and a HPA for a Servable") {
      val name = "asd"
      val modelVersionId = 1
      val image = DockerImage("image", "tag")
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
      ).some

      val client = mock[KubernetesClient[IO]]
      val cloudInstance = CloudInstance(
        modelVersionId = modelVersionId,
        name = name,
        status = CloudInstance.Status.Starting
      )
      when(client.runDeployment(name, cloudInstance, image, config)).thenReturn(
        Deployment(name)
          .pure[IO]
      )
      when(client.runService(name, cloudInstance)).thenReturn(
        Service(name)
          .addLabel(CloudDriver.Labels.ModelVersionId -> modelVersionId.toString)
          .addLabel(CloudDriver.Labels.ServiceName -> name)
          .withClusterIP("127.0.0.1")
          .exposeOnPort(Service.Port(name = "grpc", port = 9090))
          .pure[IO]
      )
      when(client.createHPA(name, name, "apps/v1", "Deployment", hpaConfig)).thenReturn(
        HorizontalPodAutoscaler(
          metadata = skuber.ObjectMeta(),
          spec = HorizontalPodAutoscaler.Spec(
            scaleTargetRef = CrossVersionObjectReference(
              apiVersion = "apps/v1",
              kind = "Deployment",
              name = name
            )
          )
        ).pure[IO]
      )

      val driver = new KubernetesDriver[IO](client)

      val result = driver.run(
        name = name,
        modelVersionId = modelVersionId,
        image = image,
        config = config
      ).unsafeRunSync()
      assert(result.status === CloudInstance.Status.Running("127.0.0.1", 9090))
    }
  }
}