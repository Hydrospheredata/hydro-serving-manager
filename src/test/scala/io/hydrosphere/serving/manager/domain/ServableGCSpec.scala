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
import io.hydrosphere.serving.manager.domain.monitoring.{Monitoring, MonitoringRepository}
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableGC, ServableRepository, ServableService}

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
        status = ModelVersionStatus.

      )

      implicit val appRepo = mock[ApplicationRepository[IO]]
      when(appRepo.findVersionUsage(1)).thenReturn(Nil.pure[IO])

      val servable = Servable(

      )

      implicit val servableRepo = mock[ServableRepository[IO]]
      when(servableRepo.findForModelVersion(1)).thenReturn()

      implicit val servableService = mock[ServableService[IO]]

      implicit val monRepo = mock[MonitoringRepository[IO]]
      implicit val monService = mock[Monitoring[IO]]

      ServableGC.gcModelVersion[IO](1).unsafeRunSync()

      pending
    }
  }
}
