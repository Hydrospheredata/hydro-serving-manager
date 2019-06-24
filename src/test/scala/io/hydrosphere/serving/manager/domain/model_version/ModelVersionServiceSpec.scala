package io.hydrosphere.serving.manager.domain.model_version

import java.time.LocalDateTime

import cats.effect.IO
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.discovery.{DiscoveryEvent, ModelPublisher}
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model

import scala.collection.mutable.ListBuffer

class ModelVersionServiceSpec extends GenericUnitTest {
  describe("ModelVersionService") {
    it("should calculate first version") {
      val versionRepo = mock[ModelVersionRepository[IO]]
      when(versionRepo.lastModelVersionByModel(1L, 1)).thenReturn(IO(Seq.empty))
      val versionService = ModelVersionService.apply[IO](
        modelVersionRepository = versionRepo,
        applicationRepo = null,
        modelPublisher = null
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
        applicationRepo = null,
        modelPublisher = null
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
        applicationRepo = null,
        modelPublisher = null
      )
      assert(versionService.getNextModelVersion(1).unsafeRunSync() === 3)
    }
    it("should notify when version deletes") {
      val versionRepo = new ModelVersionRepository[IO] {
        override def create(entity: ModelVersion): IO[ModelVersion] = ???

        override def update(id: Long, entity: ModelVersion): IO[Int] = ???

        override def get(id: Long): IO[Option[ModelVersion]] = IO {
          Some(
            ModelVersion(
              id = 1,
              image = DockerImage("", ""),
              created = LocalDateTime.now(),
              finished = None,
              modelVersion = 4,
              modelContract = ModelContract.defaultInstance,
              runtime = DockerImage("", ""),
              model = Model(1, "aaaa"),
              hostSelector = None,
              status = ModelVersionStatus.Assembling,
              profileTypes = Map.empty,
              installCommand = None,
              metadata = Map.empty
            )
          )
        }

        override def get(modelName: String, modelVersion: Long): IO[Option[ModelVersion]] = ???

        override def get(idx: Seq[Long]): IO[Seq[ModelVersion]] = ???

        override def delete(id: Long): IO[Int] = IO(1)

        override def all(): IO[Seq[ModelVersion]] = ???

        override def listForModel(modelId: Long): IO[Seq[ModelVersion]] = ???

        override def lastModelVersionByModel(modelId: Long, max: Int): IO[Seq[ModelVersion]] = ???
      }
      val events = ListBuffer.empty[Long]
      val modelPub = new ModelPublisher[IO] {
        override def publish(t: DiscoveryEvent[ModelVersion, Long]): IO[Unit] = {
          t match {
            case DiscoveryEvent.Initial => IO.unit
            case DiscoveryEvent.ItemUpdate(_) => IO.raiseError(new RuntimeException("Unreachable"))
            case DiscoveryEvent.ItemRemove(items) => IO(events ++= items)
          }
        }
      }
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
          when(versionRepo.lastModelVersionByModel(1, 1)).thenReturn(
            IO(Seq.empty)
          )
          val versionService = ModelVersionService[IO](versionRepo, null, null)
          versionService.getNextModelVersion(1).map { x =>
            assert(x === 1)
          }
        }
      }

      it("for a built model") {
        ioAssert {
          val versionRepo = mock[ModelVersionRepository[IO]]
          when(versionRepo.lastModelVersionByModel(1, 1)).thenReturn(
            IO(Seq(ModelVersion(
              id = 1,
              image = DockerImage("", ""),
              created = LocalDateTime.now(),
              finished = None,
              modelVersion = 4,
              modelContract = ModelContract.defaultInstance,
              runtime = DockerImage("", ""),
              model = Model(1, "aaaa"),
              hostSelector = None,
              status = ModelVersionStatus.Assembling,
              profileTypes = Map.empty,
              installCommand = None,
              metadata = Map.empty
            )))
          )
          val versionService = ModelVersionService[IO](versionRepo, null, null)
          versionService.getNextModelVersion(1).map { x =>
            assert(x === 5)
          }
        }
      }
    }
  }

}
