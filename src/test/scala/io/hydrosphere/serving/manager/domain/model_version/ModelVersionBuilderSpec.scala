package io.hydrosphere.serving.manager.domain.model_version

import java.nio.file.{Path, Paths}
import cats.implicits._
import cats.effect.{Concurrent, IO, Resource}
import com.spotify.docker.client.messages._
import com.spotify.docker.client.ProgressHandler
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.domain.image.{DockerImage, ImageRepository}
import io.hydrosphere.serving.manager.domain.model.{Model, ModelVersionMetadata}
import io.hydrosphere.serving.manager.domain.model_build.{BuildLoggingService, ModelVersionBuilder}
import io.hydrosphere.serving.manager.infrastructure.docker.DockerdClient
import io.hydrosphere.serving.manager.infrastructure.storage.{ModelFileStructure, StorageOps}
import io.hydrosphere.serving.manager.util.DockerProgress
import org.mockito.invocation.InvocationOnMock

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.util.control.NoStackTrace

class ModelVersionBuilderSpec extends GenericUnitTest {
  describe("ModelVersionBuilder") {
    it("should handle push error") {
      ioAssert {
        val dc = mock[DockerdClient[IO]]
        when(
          dc.build(any, any, any, any, any)
        ).thenReturn("sha:random-sha".pure[IO])
        when(dc.push(any, any, any))
          .thenReturn(IO.raiseError(new RuntimeException with NoStackTrace))

        val model = Model(1, "push-me")

        val versionRepo = mock[ModelVersionRepository[IO]]
        when(versionRepo.create(any)).thenAnswer { (invocation: InvocationOnMock) =>
          invocation.getArguments.head.asInstanceOf[ModelVersion].pure[IO]
        }
        when(versionRepo.update(any)).thenReturn(1.pure[IO])

        val modelVersionMetadata = ModelVersionMetadata(
          modelName = model.name,
          signature = Signature.defaultSignature,
          runtime = DockerImage("run", "time"),
          installCommand = None,
          metadata = Map.empty
        )

        val imageRepo = new ImageRepository[IO] {
          override def getImage(name: String, tag: String): DockerImage = DockerImage(name, tag)

          override def push(dockerImage: DockerImage, progressHandler: ProgressHandler): IO[Unit] =
            IO.raiseError(new Exception())
        }

        val versionService = mock[ModelVersionService[IO]]
        when(versionService.getNextModelVersion(anyLong)) thenReturn IO.pure(1)

        val mfs = ModelFileStructure(
          Paths.get(""),
          Paths.get(""),
          Paths.get(""),
          Paths.get(""),
          Paths.get("")
        )

        val ops = mock[StorageOps[IO]]
        when(ops.writeBytes(any[Path], any[Array[Byte]])) thenAnswer { (path: Path) =>
          IO.pure(path)
        }

        val bl = new BuildLoggingService[IO] {
          override def logger(modelVersion: ModelVersion.Internal): Resource[IO, ProgressHandler] =
            Resource.make(IO(DockerProgress.makeLogger(println)))(_ => IO.unit)
          override def getLogs(
              modelVersionId: Long,
              sinceLine: Int
          ): IO[Option[fs2.Stream[IO, String]]] = IO(None)
        }

        val builder = ModelVersionBuilder[IO]()(
          Concurrent[IO],
          modelVersionRepository = versionRepo,
          imageRepository = imageRepo,
          modelVersionService = versionService,
          storageOps = ops,
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

        val versionRepo: ModelVersionRepository[IO] = mock[ModelVersionRepository[IO]]
        when(versionRepo.create(any[ModelVersion])) thenAnswer { (entity: ModelVersion) =>
          IO.pure(entity)
        }
        when(versionRepo.update(any[ModelVersion])) thenAnswer IO.pure(1)

        val modelVersionMetadata = ModelVersionMetadata(
          modelName = model.name,
          signature = Signature.defaultSignature,
          runtime = DockerImage("run", "time"),
          installCommand = None,
          metadata = Map.empty
        )

        val dc = mock[DockerdClient[IO]]
        when(dc.build(any, any, any, any, any)) thenReturn IO("random-sha")
        when(dc.push(any, any, any)) thenReturn IO.unit
        when(dc.inspectImage(any)) thenAnswer { (id: String) =>
          val imgInfo: ImageInfo = mock[ImageInfo]
          when(imgInfo.id) thenReturn id
          IO(imgInfo)
        }

        val p = Promise[DockerImage]()
        val imageRepo = new ImageRepository[IO] {
          override def getImage(name: String, tag: String): DockerImage = DockerImage(name, tag)

          override def push(dockerImage: DockerImage, progressHandler: ProgressHandler): IO[Unit] =
            IO(p.success(dockerImage))
        }

        val versionService = mock[ModelVersionService[IO]]
        when(versionService.getNextModelVersion(anyLong)) thenReturn IO(1)

        val mfs = ModelFileStructure(
          Paths.get(""),
          Paths.get(""),
          Paths.get(""),
          Paths.get(""),
          Paths.get("")
        )

        val ops = mock[StorageOps[IO]]
        when(ops.writeBytes(any, any)).thenAnswer[Path, Array[Byte]]((path: Path, _: Array[Byte]) =>
          IO(path)
        )

        val bl = new BuildLoggingService[IO] {
          override def logger(modelVersion: ModelVersion.Internal): Resource[IO, ProgressHandler] =
            Resource.make(IO(DockerProgress.makeLogger(println)))(_ => IO.unit)

          override def getLogs(
              modelVersionId: Long,
              sinceLine: Int
          ): IO[Option[fs2.Stream[IO, String]]] = IO(None)
        }
        val builder = ModelVersionBuilder[IO]()(
          Concurrent[IO],
          modelVersionRepository = versionRepo,
          imageRepository = imageRepo,
          modelVersionService = versionService,
          storageOps = ops,
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
          assert(startedBuild.image == DockerImage("push-me", "1"))
          val pushedImage = Await.result(p.future, 20.seconds)
          assert(pushedImage.fullName == completedBuild.image.fullName)
          assert(startedBuild.model.name == completedBuild.model.name)
          assert(startedBuild.modelVersion == completedBuild.modelVersion)
          assert(DockerImage("push-me", "1", Some("random-sha")) === completedBuild.image)
        }
      }
    }
  }
}
