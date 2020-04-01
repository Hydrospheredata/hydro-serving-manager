package io.hydrosphere.serving.manager.domain.model_version

import java.io.File
import java.nio.file.{Path, Paths}

import cats.effect.{Concurrent, IO}
import com.spotify.docker.client.{DockerClient, LogStream, ProgressHandler}
import com.spotify.docker.client.messages.{Container, ContainerConfig, ContainerCreation, ImageInfo, ProgressMessage, RegistryAuth}
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.discovery.DiscoveryEvent
import io.hydrosphere.serving.manager.domain.image.{DockerImage, ImageBuilder, ImageRepository}
import io.hydrosphere.serving.manager.domain.model.{Model, ModelVersionMetadata}
import io.hydrosphere.serving.manager.domain.model_build.{BuildLoggingService, ModelVersionBuilder}
import io.hydrosphere.serving.manager.infrastructure.docker.DockerdClient
import io.hydrosphere.serving.manager.infrastructure.storage.{ModelFileStructure, StorageOps}
import io.hydrosphere.serving.manager.util.DockerProgress
import org.mockito.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Promise}

class ModelVersionBuilderSpec extends GenericUnitTest {
  describe("ModelVersionBuilder") {
    val dc = new DockerdClient[IO] {
      override def createContainer(container: ContainerConfig, name: Option[String]): IO[ContainerCreation] = ???
      override def runContainer(id: String): IO[Unit] = ???
      override def removeContainer(id: String, params: List[DockerClient.RemoveContainerParam]): IO[Unit] = ???
      override def listContainers(params: List[DockerClient.ListContainersParam]): IO[List[Container]] = ???
      override def logs(id: String, follow: Boolean): IO[LogStream] = ???
      override def build(directory: Path, name: String, dockerfile: String, handler: ProgressHandler, params: List[DockerClient.BuildParam]): IO[String] = ???
      override def push(image: String, progressHandler: ProgressHandler, registryAuth: RegistryAuth): IO[Unit] = ???
      override def inspectImage(image: String): IO[ImageInfo] = ???
      override def getHost: IO[String] = ???
    }
    it("should handle push error") {
      ioAssert {
        val model = Model(1, "push-me")

        val versionRepo = new ModelVersionRepository[IO] {
          override def create(entity: ModelVersion): IO[ModelVersion] = IO.pure(entity)
          override def get(id: Long): IO[Option[ModelVersion]] = ???
          override def get(modelName: String, modelVersion: Long): IO[Option[ModelVersion]] = ???
          override def delete(id: Long): IO[Int] = ???
          override def update(entity: ModelVersion): IO[Int] = IO(1)
          override def all(): IO[List[ModelVersion]] = ???
          override def listForModel(modelId: Long): IO[List[ModelVersion]] = ???
          override def lastModelVersionByModel(modelId: Long): IO[Option[ModelVersion]] = ???
        }

        val modelVersionMetadata = ModelVersionMetadata(
          modelName = model.name,
          contract = ModelContract.defaultInstance,
          runtime = DockerImage("run", "time"),
          hostSelector = None,
          installCommand = None,
          metadata = Map.empty
        )

        val imageBuilder = mock[ImageBuilder[IO]]
        when(imageBuilder.build(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(
          IO.pure("random-sha")
        )

        val imageRepo = new ImageRepository[IO] {
          override def getImage(name: String, tag: String): DockerImage = DockerImage(name, tag)

          override def push(dockerImage: DockerImage, progressHandler: ProgressHandler): IO[Unit] = {
            IO.raiseError(new Exception())
          }
        }
        val versionService = new ModelVersionService[IO] {
          override def getNextModelVersion(modelId: Long) = IO.pure(1)
          override def get(name: String, version: Long) = ???
          override def list = ???
          override def delete(versionId: Long) = ???
          override def all(): IO[List[ModelVersion]] = ???
          override def get(id: Long): IO[ModelVersion] = ???
          override def listForModel(modelId: Long): IO[List[ModelVersion]] = ???
        }

        val mfs = ModelFileStructure(
          Paths.get(""),
          Paths.get(""),
          Paths.get(""),
          Paths.get(""),
          Paths.get("")
        )
        val ops = new StorageOps[IO] {
          override def getReadableFile(path: Path): IO[Option[File]] = ???
          override def getAllFiles(folder: Path): IO[Option[List[String]]] = ???
          override def getSubDirs(path: Path): IO[Option[List[String]]] = ???
          override def exists(path: Path): IO[Boolean] = ???
          override def copyFile(src: Path, target: Path): IO[Path] = ???
          override def moveFolder(src: Path, target: Path): IO[Path] = ???
          override def removeFolder(path: Path): IO[Option[Unit]] = ???
          override def getTempDir(prefix: String): IO[Path] = ???
          override def readText(path: Path): IO[Option[List[String]]] = ???
          override def readBytes(path: Path): IO[Option[Array[Byte]]] = ???
          override def writeBytes(path: Path, bytes: Array[Byte]): IO[Path] = IO.pure(path)
        }
        val mh = new ModelVersionEvents.Publisher[IO] {
          override def publish(t: DiscoveryEvent[ModelVersion, Long]): IO[Unit] = IO.unit
        }
        val bl = new BuildLoggingService[IO] {
          override def makeLogger(modelVersion: ModelVersion.Internal): IO[ProgressHandler] = IO(DockerProgress.makeLogger(println))
          override def finishLogging(modelVersion: Long): IO[Option[Unit]] = IO(Some(()))
          override def getLogs(modelVersionId: Long, sinceLine: Int): IO[Option[fs2.Stream[IO, String]]] = IO(None)
        }


        val builder = ModelVersionBuilder[IO]()(
          Concurrent[IO],
          modelVersionRepository = versionRepo,
          imageRepository = imageRepo,
          modelVersionService = versionService,
          storageOps = ops,
          modelDiscoveryHub = mh,
          buildLoggingService = bl,
          dockerClient = dc
        )
        for {
          stateful <- builder.build(model, modelVersionMetadata, mfs)
          startedBuild = stateful.started
          completedBuild <- stateful.completed.get
        } yield {
          assert(startedBuild.model.name === "push-me")
          assert(startedBuild.modelVersion === 1)
          assert(startedBuild.image === DockerImage("push-me", "1"))
          assert(startedBuild.model.name === completedBuild.model.name)
          assert(startedBuild.modelVersion === completedBuild.modelVersion)
          assert(DockerImage("push-me", "1", Some("random-sha")) === completedBuild.image)
        }
      }
    }
    it("should push the built image") {
      ioAssert {
        val model = Model(1, "push-me")

        val versionRepo = new ModelVersionRepository[IO] {
          override def create(entity: ModelVersion): IO[ModelVersion] = IO.pure(entity)
          override def get(id: Long): IO[Option[ModelVersion]] = ???
          override def get(modelName: String, modelVersion: Long): IO[Option[ModelVersion]] = ???
          override def delete(id: Long): IO[Int] = ???
          override def update(entity: ModelVersion): IO[Int] = IO(1)
          override def all(): IO[List[ModelVersion]] = ???
          override def listForModel(modelId: Long): IO[List[ModelVersion]] = ???
          override def lastModelVersionByModel(modelId: Long): IO[Option[ModelVersion]] = ???
        }

        val modelVersionMetadata = ModelVersionMetadata(
          modelName = model.name,
          contract = ModelContract.defaultInstance,
          runtime = DockerImage("run", "time"),
          hostSelector = None,
          installCommand = None,
          metadata = Map.empty
        )

        val imageBuilder = mock[ImageBuilder[IO]]
        when(imageBuilder.build(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(
          IO.pure("random-sha")
        )

        val p = Promise[DockerImage]
        val imageRepo = new ImageRepository[IO] {
          override def getImage(name: String, tag: String): DockerImage = DockerImage(name, tag)

          override def push(dockerImage: DockerImage, progressHandler: ProgressHandler): IO[Unit] = {
              IO(p.success(dockerImage))
          }
        }
        val versionService = new ModelVersionService[IO] {
          override def getNextModelVersion(modelId: Long) = IO.pure(1)
          override def get(name: String, version: Long) = ???
          override def list = ???
          override def delete(versionId: Long) = ???
          override def all(): IO[List[ModelVersion]] = ???
          override def get(id: Long): IO[ModelVersion] = ???
          override def listForModel(modelId: Long): IO[List[ModelVersion]] = ???
        }

        val mfs = ModelFileStructure(
          Paths.get(""),
          Paths.get(""),
          Paths.get(""),
          Paths.get(""),
          Paths.get("")
        )
        val ops = new StorageOps[IO] {
          override def getReadableFile(path: Path): IO[Option[File]] = ???
          override def getAllFiles(folder: Path): IO[Option[List[String]]] = ???
          override def getSubDirs(path: Path): IO[Option[List[String]]] = ???
          override def exists(path: Path): IO[Boolean] = ???
          override def copyFile(src: Path, target: Path): IO[Path] = ???
          override def moveFolder(src: Path, target: Path): IO[Path] = ???
          override def removeFolder(path: Path): IO[Option[Unit]] = ???
          override def getTempDir(prefix: String): IO[Path] = ???
          override def readText(path: Path): IO[Option[List[String]]] = ???
          override def readBytes(path: Path): IO[Option[Array[Byte]]] = ???
          override def writeBytes(path: Path, bytes: Array[Byte]): IO[Path] = IO.pure(path)
        }
        val mh = new ModelVersionEvents.Publisher[IO] {
          override def publish(t: DiscoveryEvent[ModelVersion, Long]): IO[Unit] = IO.unit
        }
        val bl = new BuildLoggingService[IO] {
          override def makeLogger(modelVersion: ModelVersion.Internal): IO[ProgressHandler] = IO(DockerProgress.makeLogger(println))
          override def finishLogging(modelVersion: Long): IO[Option[Unit]] = IO(Some(()))
          override def getLogs(modelVersionId: Long, sinceLine: Int): IO[Option[fs2.Stream[IO, String]]] = IO(None)
        }
        val builder = ModelVersionBuilder[IO]()(
          Concurrent[IO],
          modelVersionRepository = versionRepo,
          imageRepository = imageRepo,
          modelVersionService = versionService,
          storageOps = ops,
          modelDiscoveryHub = mh,
          buildLoggingService = bl,
          dockerClient = dc
        )
        for {
          stateful <- builder.build(model, modelVersionMetadata, mfs)
          startedBuild = stateful.started
          completedBuild <- stateful.completed.get
        } yield {
          assert(startedBuild.model.name === "push-me")
          assert(startedBuild.modelVersion === 1)
          assert(startedBuild.image === DockerImage("push-me", "1"))
          val pushedImage = Await.result(p.future, 20.seconds)
          assert(pushedImage.fullName === completedBuild.image.fullName)
          assert(startedBuild.model.name === completedBuild.model.name)
          assert(startedBuild.modelVersion === completedBuild.modelVersion)
          assert(DockerImage("push-me", "1", Some("random-sha")) === completedBuild.image)
        }
      }
    }
  }
}
