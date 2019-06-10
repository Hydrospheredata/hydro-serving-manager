package io.hydrosphere.serving.manager.domain.model_version

import java.time.LocalDateTime

import cats.effect.IO
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model

class ModelVersionServiceSpec extends GenericUnitTest {
  describe("ModelVersionService") {
    it("should calculate first version") {
      val versionRepo = mock[ModelVersionRepository[IO]]
      when(versionRepo.lastModelVersionByModel(1L, 1)).thenReturn(IO(Seq.empty))
      val versionService = ModelVersionService.apply[IO](
        modelVersionRepository = versionRepo,
        applicationRepo = null
      )
      assert(versionService.getNextModelVersion(1).unsafeRunSync() === 1)
    }
    it("should calculate second version") {
      val versionRepo = mock[ModelVersionRepository[IO]]
      when(versionRepo.lastModelVersionByModel(1L, 1)).thenReturn(IO(
        Seq(ModelVersion(
          id = 1,
          image = DockerImage("asd", "asd"),
          created = LocalDateTime.now(),
          finished = None,
          modelVersion = 1,
          modelContract = ModelContract.defaultInstance,
          runtime = DockerImage("asd", "asd"),
          model = Model(1, "asd"),
          hostSelector = None,
          status = ModelVersionStatus.Released,
          profileTypes = Map.empty,
          installCommand = None,
          metadata = Map.empty
        )))
      )
      val versionService = ModelVersionService.apply[IO](
        modelVersionRepository = versionRepo,
        applicationRepo = null
      )
      assert(versionService.getNextModelVersion(1).unsafeRunSync() === 2)
    }
    it("should calculate third version") {
      val versionRepo = mock[ModelVersionRepository[IO]]
      when(versionRepo.lastModelVersionByModel(1L, 1)).thenReturn(IO(
        Seq(ModelVersion(
          id = 1,
          image = DockerImage("asd", "asd"),
          created = LocalDateTime.now(),
          finished = None,
          modelVersion = 2,
          modelContract = ModelContract.defaultInstance,
          runtime = DockerImage("asd", "asd"),
          model = Model(1, "asd"),
          hostSelector = None,
          status = ModelVersionStatus.Released,
          profileTypes = Map.empty,
          installCommand = None,
          metadata = Map.empty
        )))
      )
      val versionService = ModelVersionService.apply[IO](
        modelVersionRepository = versionRepo,
        applicationRepo = null
      )
      assert(versionService.getNextModelVersion(1).unsafeRunSync() === 3)
    }
  }

}
