package io.hydrosphere.serving.manager.domain

import java.time.Instant

import cats.effect.IO
import cats.implicits._
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.application.ApplicationRepository
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionStatus}
import io.hydrosphere.serving.manager.domain.monitoring.{CustomModelMetricSpec, CustomModelMetricSpecConfiguration, Monitoring, MonitoringRepository, ThresholdCmpOperator}
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableGC, ServableRepository, ServableService}
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
        modelContract = ModelContract.defaultInstance,
        runtime = DockerImage("runtime", "tag"),
        status = ModelVersionStatus.Released,
        installCommand = None,
        metadata = Map.empty
      )

      implicit val appRepo = mock[ApplicationRepository[IO]]
      when(appRepo.findVersionUsage(mv.id)).thenReturn(Nil.pure[IO])

      val servable = Servable(
        modelVersion = mv,
        nameSuffix = "asdasd",
        status = Servable.Serving("ok", "localhost", 9090),
        usedApps = List.empty
      )
      val metricServable = Servable(
        modelVersion = mv,
        nameSuffix = "monitoring",
        status = Servable.Serving("ok", "localhost", 9090),
        usedApps = List.empty
      )

      implicit val servableRepo = mock[ServableRepository[IO]]
      when(servableRepo.findForModelVersion(mv.id)).thenReturn(List(servable).pure[IO])

      implicit val servableService = mock[ServableService[IO]]
      when(servableService.stop(servable.fullName)).thenReturn(servable.pure[IO])
      when(servableService.stop(metricServable.fullName)).thenReturn(metricServable.pure[IO])

      val metric1 =
        CustomModelMetricSpec(
          name = "test-1",
          modelVersionId = mv.id,
          config = CustomModelMetricSpecConfiguration(
            modelVersionId = mv.id,
            threshold = 1,
            thresholdCmpOperator = ThresholdCmpOperator.Less,
            servable = metricServable.some
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
            servable = None
          )
        )

      implicit val monRepo = mock[MonitoringRepository[IO]]
      when(monRepo.forModelVersion(mv.id)).thenReturn(List(metric1, metric2).pure[IO])
      implicit val monService = mock[Monitoring[IO]]
      when(monService.update(metric1Cleaned)).thenReturn(metric1.pure[IO])
      when(monService.update(metric2)).thenReturn(metric2.pure[IO])

      ServableGC.gcModelVersion[IO](mv.id).unsafeRunSync()

      Mockito.verify(appRepo).findVersionUsage(mv.id)
      Mockito.verify(servableService).stop(servable.fullName)
      Mockito.verify(servableService).stop(metricServable.fullName)
      succeed
    }
  }
}
