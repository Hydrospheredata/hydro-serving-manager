package io.hydrosphere.serving.manager.domain

import java.time.Instant
import cats.effect.IO
import cats.implicits._
import com.amazonaws.services.ecs.model.transform.DeploymentConfigurationJsonUnmarshaller
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.application.ApplicationRepository
import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfiguration
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionStatus}
import io.hydrosphere.serving.manager.domain.monitoring.{
  CustomModelMetricSpec,
  CustomModelMetricSpecConfiguration,
  Monitoring,
  MonitoringRepository,
  ThresholdCmpOperator
}
import io.hydrosphere.serving.manager.domain.servable.{
  Servable,
  ServableGC,
  ServableRepository,
  ServableService
}
import io.hydrosphere.serving.proto.contract.signature.ModelSignature
import org.mockito.Mockito

class ServableGCSpec extends GenericUnitTest {
  describe("Servable Garbage Collector") {
    it("should collect orphaned servables") {
      val mv = ModelVersion.Internal(
        id = 1,
        model = Model(1, "test-model"),
        image = DockerImage("name", "tag"),
        created = Instant.now(),
        finished = Instant.now().some,
        modelVersion = 1,
        modelSignature = Signature.defaultSignature,
        runtime = DockerImage("runtime", "tag"),
        status = ModelVersionStatus.Released,
        installCommand = None,
        metadata = Map.empty
      )

      implicit val appRepo: ApplicationRepository[IO] = mock[ApplicationRepository[IO]]
      when(appRepo.findVersionUsage(mv.id)).thenReturn(Nil.pure[IO])

      val servable = Servable(
        modelVersion = mv,
        name = "asdasd",
        status = Servable.Status.Serving,
        usedApps = List.empty,
        port = Some(9090),
        host = Some("localhost"),
        message = "ok".some,
        deploymentConfiguration = DeploymentConfiguration.empty
      )
      val metricServable = Servable(
        modelVersion = mv,
        name = "monitoring",
        status = Servable.Status.Serving,
        usedApps = List.empty,
        port = Some(9090),
        host = Some("localhost"),
        message = "ok".some,
        deploymentConfiguration = DeploymentConfiguration.empty
      )

      implicit val servableRepo: ServableRepository[IO] = mock[ServableRepository[IO]]
      when(servableRepo.findForModelVersion(mv.id)).thenReturn(List(servable).pure[IO])

      implicit val servableService: ServableService[IO] = mock[ServableService[IO]]
      when(servableService.stop(servable.name)).thenReturn(servable.pure[IO])
      when(servableService.stop(metricServable.name)).thenReturn(metricServable.pure[IO])

      val metric1 =
        CustomModelMetricSpec(
          name = "test-1",
          modelVersionId = mv.id,
          config = CustomModelMetricSpecConfiguration(
            modelVersionId = mv.id,
            threshold = 1,
            thresholdCmpOperator = ThresholdCmpOperator.Less,
            servable = metricServable.some,
            deploymentConfigName = None
          )
        )
      val metric1Cleaned = metric1.copy(config = metric1.config.copy(servable = None))
      val metric2 =
        CustomModelMetricSpec(
          name = "test-2",
          modelVersionId = mv.id,
          config = CustomModelMetricSpecConfiguration(
            modelVersionId = mv.id,
            threshold = 1,
            thresholdCmpOperator = ThresholdCmpOperator.Less,
            servable = None,
            deploymentConfigName = None
          )
        )

      implicit val monRepo: MonitoringRepository[IO] = mock[MonitoringRepository[IO]]
      when(monRepo.forModelVersion(mv.id)).thenReturn(List(metric1, metric2).pure[IO])

      implicit val monService: Monitoring[IO] = mock[Monitoring[IO]]
      when(monService.update(metric1Cleaned)).thenReturn(metric1.pure[IO])
      when(monService.update(metric2)).thenReturn(metric2.pure[IO])

      ServableGC.gcModelVersion[IO](mv.id).unsafeRunSync()

      Mockito.verify(appRepo).findVersionUsage(mv.id)
      Mockito.verify(servableService).stop(servable.name)
      Mockito.verify(servableService).stop(metricServable.name)
      succeed
    }
  }
}
