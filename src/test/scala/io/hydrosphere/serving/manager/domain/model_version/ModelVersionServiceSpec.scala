package io.hydrosphere.serving.manager.domain.model_version

import java.time.Instant

import cats.effect.IO
import cats.implicits._
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.discovery.DiscoveryEvent
import io.hydrosphere.serving.manager.domain.model.Model

import scala.collection.mutable.ListBuffer

class ModelVersionServiceSpec extends GenericUnitTest {
  describe("ModelVersionService") {
    it("should calculate first version") {
      val versionRepo = mock[ModelVersionRepository[IO]]
      when(versionRepo.lastModelVersionByModel(1)).thenReturn(IO(None))
      val versionService = ModelVersionService.apply[IO](
        modelVersionRepository = versionRepo,
        applicationRepo = null,
        modelPublisher = null
      )
      assert(versionService.getNextModelVersion(1).unsafeRunSync() == 1)
    }

    it("should calculate second version") {
      val versionRepo = mock[ModelVersionRepository[IO]]
      when(versionRepo.lastModelVersionByModel(1L)).thenReturn(
        IO(
          Some(
            ModelVersion.Internal(
              id = 1,
              image = dummyImage,
              created = Instant.now(),
              finished = None,
              modelVersion = 1,
              modelContract = defaultContract,
              runtime = dummyImage,
              model = Model(1, "asd"),
              hostSelector = None,
              status = ModelVersionStatus.Released,
              installCommand = None,
              metadata = Map.empty
            )
          )
        )
      )
      val versionService = ModelVersionService.apply[IO](
        modelVersionRepository = versionRepo,
        applicationRepo = null,
        modelPublisher = null
      )
      assert(versionService.getNextModelVersion(1).unsafeRunSync() == 2)
    }
    it("should calculate third version") {
      val versionRepo = mock[ModelVersionRepository[IO]]
      when(versionRepo.lastModelVersionByModel(1L)).thenReturn(
        IO(
          Some(
            ModelVersion.Internal(
              id = 1,
              image = dummyImage,
              created = Instant.now(),
              finished = None,
              modelVersion = 2,
              modelContract = defaultContract,
              runtime = dummyImage,
              model = Model(1, "asd"),
              hostSelector = None,
              status = ModelVersionStatus.Released,
              installCommand = None,
              metadata = Map.empty
            )
          )
        )
      )
      val versionService = ModelVersionService.apply[IO](
        modelVersionRepository = versionRepo,
        applicationRepo = null,
        modelPublisher = null
      )
      assert(versionService.getNextModelVersion(1).unsafeRunSync() == 3)
    }
    it("should notify when version deletes") {
      val versionRepo = mock[ModelVersionRepository[IO]]
      when(versionRepo.get(anyLong)).thenReturn(IO {
        Some(
          ModelVersion.Internal(
            id = 1,
            image = dummyImage,
            created = Instant.now(),
            finished = None,
            modelVersion = 4,
            modelContract = defaultContract,
            runtime = dummyImage,
            model = Model(1, "aaaa"),
            hostSelector = None,
            status = ModelVersionStatus.Assembling,
            installCommand = None,
            metadata = Map.empty
          )
        )
      })
      when(versionRepo.delete(anyLong)).thenReturn(1.pure[IO])

      val events   = ListBuffer.empty[Long]
      val modelPub = mock[ModelVersionEvents.Publisher[IO]]
      when(modelPub.remove(any)).thenAnswer[Long](item => IO(events += item))
      val versionService = ModelVersionService[IO](
        modelVersionRepository = versionRepo,
        applicationRepo = null,
        modelPublisher = modelPub
      )
      versionService.delete(1).unsafeRunSync()
      assert(events.contains(1))
    }

    describe("should calculate the right version") {
      it("for a new model") {
        ioAssert {
          val versionRepo = mock[ModelVersionRepository[IO]]
          when(versionRepo.lastModelVersionByModel(1)).thenReturn(None.pure[IO])
          val versionService =
            ModelVersionService[IO](versionRepo, null, null)
          versionService.getNextModelVersion(1).map(x => assert(x == 1))
        }
      }

      it("for a built model") {
        ioAssert {
          val versionRepo = mock[ModelVersionRepository[IO]]
          when(versionRepo.lastModelVersionByModel(1)).thenReturn(
            IO(
              Some(
                ModelVersion.Internal(
                  id = 1,
                  image = dummyImage,
                  created = Instant.now(),
                  finished = None,
                  modelVersion = 4,
                  modelContract = defaultContract,
                  runtime = dummyImage,
                  model = Model(1, "aaaa"),
                  hostSelector = None,
                  status = ModelVersionStatus.Assembling,
                  installCommand = None,
                  metadata = Map.empty
                )
              )
            )
          )
          val versionService = ModelVersionService[IO](versionRepo, null, null)
          versionService.getNextModelVersion(1).map(x => assert(x == 5))
        }
      }
    }
  }
}
