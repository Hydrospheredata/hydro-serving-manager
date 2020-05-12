package io.hydrosphere.serving.manager.domain.model_version

import java.nio.file.{Path, Paths}

import cats.effect.IO
import cats.effect.concurrent.Deferred
import cats.implicits._
import com.spotify.docker.client.ProgressHandler
import com.spotify.docker.client.messages._
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.image.{DockerImage, ImageRepository}
import io.hydrosphere.serving.manager.domain.model.{Model, ModelVersionMetadata}
import io.hydrosphere.serving.manager.domain.model_build.{BuildLoggingService, ModelVersionBuilder}
import io.hydrosphere.serving.manager.infrastructure.docker.DockerdClient
import io.hydrosphere.serving.manager.infrastructure.storage.{ModelFileStructure, StorageOps}
import io.hydrosphere.serving.manager.util.DockerProgress

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.control.NoStackTrace

class ModelVersionBuilderSpec extends GenericUnitTest {
  describe("ModelVersionBuilder") {
    it("should handle push error") {
      ioAssert {
        val dc = mock[DockerdClient[IO]]
        when(dc.build(any, any, any, any, any)).thenReturn(IO("sha:random-sha"))
        when(dc.inspectImage(any)).thenAnswer[String](image =>
          IO {
            val imageInfo = mock[ImageInfo]
            when(imageInfo.id()).thenReturn(image)
            imageInfo
          }
        )
        val model = Model(1, "push-me")

        val versionRepo = mock[ModelVersionRepository[IO]]
        when(versionRepo.create(any)).thenAnswer[ModelVersion](IO.pure)
        when(versionRepo.update(any)).thenReturn(1.pure[IO])

        val modelVersionMetadata = ModelVersionMetadata(
          modelName = model.name,
          contract = defaultContract,
          runtime = DockerImage("run", "time"),
          hostSelector = None,
          installCommand = None,
          metadata = Map.empty
        )

        val imageRepo = mock[ImageRepository[IO]]
        when(imageRepo.getImageForModelVersion(any, any)).thenAnswer[String, String] {
          case (name, tag) => DockerImage(name, tag)
        }
        when(imageRepo.push(any, any)).thenReturn(IO.raiseError(new Exception()))

        val versionService = mock[ModelVersionService[IO]]
        when(versionService.getNextModelVersion(1)).thenReturn(2L.pure[IO])

        val mfs = ModelFileStructure(
          Paths.get(""),
          Paths.get(""),
          Paths.get(""),
          Paths.get(""),
          Paths.get("")
        )
        val ops = mock[StorageOps[IO]]
        when(ops.writeBytes(any, any)).thenAnswer[Path, Array[Byte]] { case (p, _) => p.pure[IO] }

        val bl = mock[BuildLoggingService[IO]]
        when(bl.makeLogger(any)).thenReturn(IO(DockerProgress.makeLogger(println)))
        when(bl.finishLogging(anyLong)).thenReturn(().some.pure[IO])

        val builder = ModelVersionBuilder[IO](
          modelVersionRepository = versionRepo,
          imageRepository = imageRepo,
          modelVersionService = versionService,
          storageOps = ops,
          modelDiscoveryHub = noopPublisher,
          buildLoggingService = bl,
          dockerClient = dc
        )
        for {
          stateful       <- builder.build(model, modelVersionMetadata, mfs)
          completedBuild <- stateful.completed.get
        } yield assert(completedBuild.status === ModelVersionStatus.Failed)
      }
    }
    it("should push the built image") {
      ioAssert {
        val model = Model(1, "push-me")

        val versionRepo = mock[ModelVersionRepository[IO]]
        when(versionRepo.create(any)).thenAnswer[ModelVersion](IO.pure)
        when(versionRepo.update(any)).thenReturn(1.pure[IO])

        val modelVersionMetadata = ModelVersionMetadata(
          modelName = model.name,
          contract = defaultContract,
          runtime = DockerImage("run", "time"),
          hostSelector = None,
          installCommand = None,
          metadata = Map.empty
        )

        val dc = mock[DockerdClient[IO]]
        when(dc.build(any, any, any, any, any)).thenReturn(IO("random-sha"))
        when(dc.inspectImage(any)).thenAnswer[String](image =>
          IO {
            val imageInfo = mock[ImageInfo]
            when(imageInfo.id()).thenReturn(image)
            imageInfo
          }
        )

        val p         = Deferred.unsafe[IO, DockerImage]
        val imageRepo = mock[ImageRepository[IO]]
        when(imageRepo.getImageForModelVersion(any, any)).thenAnswer[String, String] {
          case (name, tag) => DockerImage(name, tag)
        }
        when(imageRepo.push(any, any)).thenAnswer[DockerImage, ProgressHandler] {
          case (image, _) => p.complete(image)
        }

        val versionService = mock[ModelVersionService[IO]]
        when(versionService.getNextModelVersion(anyLong)).thenReturn(1L.pure[IO])

        val mfs = ModelFileStructure(
          Paths.get(""),
          Paths.get(""),
          Paths.get(""),
          Paths.get(""),
          Paths.get("")
        )
        val ops = mock[StorageOps[IO]]
        when(ops.writeBytes(any, any)).thenAnswer[Path, Array[Byte]]({ case (p, _) => p.pure[IO] })

        val bl = mock[BuildLoggingService[IO]]
        when(bl.makeLogger(any)).thenReturn(IO(DockerProgress.makeLogger(println)))
        when(bl.finishLogging(any)).thenReturn(().some.pure[IO])

        val builder = ModelVersionBuilder[IO](
          modelVersionRepository = versionRepo,
          imageRepository = imageRepo,
          modelVersionService = versionService,
          storageOps = ops,
          modelDiscoveryHub = noopPublisher,
          buildLoggingService = bl,
          dockerClient = dc
        )
        for {
          stateful <- builder.build(model, modelVersionMetadata, mfs)
          startedBuild = stateful.started
          completedBuild <- stateful.completed.get
        } yield {
          assert(startedBuild.model.name == "push-me")
          assert(startedBuild.modelVersion == 1)
          assert(startedBuild.image === DockerImage("push-me", "1"), startedBuild.image)
          val pushedImage = Await.result(p.get.unsafeToFuture(), 20.seconds)
          assert(pushedImage.fullName == completedBuild.image.fullName)
          assert(startedBuild.model.name == completedBuild.model.name)
          assert(startedBuild.modelVersion == completedBuild.modelVersion)
          assert(DockerImage("push-me", "1", digest = Some("random-sha")) === completedBuild.image)
        }
      }
    }
  }
}
