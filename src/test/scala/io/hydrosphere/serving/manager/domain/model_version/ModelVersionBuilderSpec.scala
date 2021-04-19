package io.hydrosphere.serving.manager.domain.model_version

import cats.data.NonEmptyList

import java.io.File
import java.lang
import java.nio.file.{Path, Paths}
import java.util.Date
import cats.implicits._
import cats.effect.{Concurrent, IO}
import com.spotify.docker.client.messages._
import com.spotify.docker.client.{DockerClient, LogMessage, LogStream, ProgressHandler}
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.discovery.DiscoveryEvent
import io.hydrosphere.serving.manager.domain.contract.DataType.DT_INT64
import io.hydrosphere.serving.manager.domain.contract.{Field, Signature, TensorShape}
import io.hydrosphere.serving.manager.domain.image.{DockerImage, ImageRepository}
import io.hydrosphere.serving.manager.domain.model.{Model, ModelVersionMetadata}
import io.hydrosphere.serving.manager.domain.model_build.{BuildLoggingService, ModelVersionBuilder}
import io.hydrosphere.serving.manager.domain.servable.CloudInstanceEvent
import io.hydrosphere.serving.manager.infrastructure.docker.DockerdClient
import io.hydrosphere.serving.manager.infrastructure.storage.{ModelFileStructure, StorageOps}
import io.hydrosphere.serving.manager.util.DockerProgress
import io.hydrosphere.serving.proto.contract.signature.ModelSignature
import org.mockito.Matchers
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
          dc.build(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())
        ).thenReturn("sha:random-sha".pure[IO])
        when(dc.push(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(IO.raiseError(new RuntimeException with NoStackTrace))

        val model = Model(1, "push-me")

        val versionRepo = mock[ModelVersionRepository[IO]]
        when(versionRepo.create(Matchers.any())).thenAnswer { (invocation: InvocationOnMock) =>
          invocation.getArguments.head.asInstanceOf[ModelVersion].pure[IO]
        }
        when(versionRepo.update(Matchers.any())).thenReturn(1.pure[IO])

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

        val versionService: ModelVersionService[IO] = new ModelVersionService[IO] {
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

          override def getSubDirs(path: Path): IO[List[String]] = ???

          override def exists(path: Path): IO[Boolean] = ???

          override def copyFile(src: Path, target: Path): IO[Path] = ???

          override def moveFolder(src: Path, target: Path): IO[Path] = ???

          override def removeFolder(path: Path): IO[Option[Unit]] = ???

          override def getTempDir(prefix: String): IO[Path] = ???

          override def readText(path: Path): IO[Option[List[String]]] = ???

          override def readBytes(path: Path): IO[Option[Array[Byte]]] = ???

          override def writeBytes(path: Path, bytes: Array[Byte]): IO[Path] = IO.pure(path)
        }

        val bl = new BuildLoggingService[IO] {
          override def makeLogger(modelVersion: ModelVersion.Internal): IO[ProgressHandler] =
            IO(DockerProgress.makeLogger(println))

          override def finishLogging(modelVersion: Long): IO[Option[Unit]] = IO(Some(()))

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
          completedBuild <- stateful.completed.get
        } yield assert(completedBuild.status === ModelVersionStatus.Failed)
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
          signature = Signature.defaultSignature,
          runtime = DockerImage("run", "time"),
          installCommand = None,
          metadata = Map.empty
        )

        val dc = new DockerdClient[IO] {
          override def createContainer(
                                        container: ContainerConfig,
                                        name: Option[String]
                                      ): IO[ContainerCreation] = ???

          override def runContainer(id: String): IO[Unit] = ???

          override def removeContainer(
                                        id: String,
                                        params: List[DockerClient.RemoveContainerParam]
                                      ): IO[Unit] = ???

          override def listContainers(
                                       params: List[DockerClient.ListContainersParam]
                                     ): IO[List[Container]] = ???

          override def logs(id: String, follow: Boolean): fs2.Stream[IO, String] = ???

          override def build(
                              directory: Path,
                              name: String,
                              dockerfile: String,
                              handler: ProgressHandler,
                              params: List[DockerClient.BuildParam]
                            ): IO[String] = IO("random-sha")

          override def push(
                             image: String,
                             progressHandler: ProgressHandler,
                             registryAuth: RegistryAuth
                           ): IO[Unit] = IO.unit

          override def inspectImage(image: String): IO[ImageInfo] =
            IO(new ImageInfo {
              override def id(): String = image

              override def parent(): String = ???

              override def comment(): String = ???

              override def created(): Date = ???

              override def container(): String = ???

              override def containerConfig(): ContainerConfig = ???

              override def dockerVersion(): String = ???

              override def author(): String = ???

              override def config(): ContainerConfig = ???

              override def architecture(): String = ???

              override def os(): String = ???

              override def size(): lang.Long = ???

              override def virtualSize(): lang.Long = ???

              override def rootFs(): RootFs = ???
            })

          override def getHost: IO[String] = ???

          override def events(): fs2.Stream[IO, CloudInstanceEvent] = ???
        }
        val p = Promise[DockerImage]
        val imageRepo = new ImageRepository[IO] {
          override def getImage(name: String, tag: String): DockerImage = DockerImage(name, tag)

          override def push(dockerImage: DockerImage, progressHandler: ProgressHandler): IO[Unit] =
            IO(p.success(dockerImage))
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

          override def getSubDirs(path: Path): IO[List[String]] = ???

          override def exists(path: Path): IO[Boolean] = ???

          override def copyFile(src: Path, target: Path): IO[Path] = ???

          override def moveFolder(src: Path, target: Path): IO[Path] = ???

          override def removeFolder(path: Path): IO[Option[Unit]] = ???

          override def getTempDir(prefix: String): IO[Path] = ???

          override def readText(path: Path): IO[Option[List[String]]] = ???

          override def readBytes(path: Path): IO[Option[Array[Byte]]] = ???

          override def writeBytes(path: Path, bytes: Array[Byte]): IO[Path] = IO.pure(path)
        }
        val bl = new BuildLoggingService[IO] {
          override def makeLogger(modelVersion: ModelVersion.Internal): IO[ProgressHandler] =
            IO(DockerProgress.makeLogger(println))

          override def finishLogging(modelVersion: Long): IO[Option[Unit]] = IO(Some(()))

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
