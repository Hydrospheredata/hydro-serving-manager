package io.hydrosphere.serving.manager.domain.model_version

import java.io.File
import java.nio.file.{Path, Paths}

import cats.effect.IO
import com.spotify.docker.client.ProgressHandler
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.discovery.{DiscoveryEvent, ModelPublisher}
import io.hydrosphere.serving.manager.domain.image.{DockerImage, ImageBuilder, ImageRepository}
import io.hydrosphere.serving.manager.domain.model.{Model, ModelVersionMetadata}
import io.hydrosphere.serving.manager.domain.model_build.{BuildLoggingService, DockerLogger, ModelVersionBuilder}
import io.hydrosphere.serving.manager.infrastructure.storage.{ModelFileStructure, StorageOps}
import io.hydrosphere.serving.manager.util.docker.InfoProgressHandler
import org.mockito.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Promise}

class ModelVersionBuilderSpec extends GenericUnitTest {
  describe("ModelVersionBuilder") {
    it("should push the built image") {
      ioAssert {
        implicit val cs = IO.contextShift(implicitly[ExecutionContext])
        val model = Model(1, "push-me")

        val versionRepo = new ModelVersionRepository[IO] {
          override def create(entity: ModelVersion): IO[ModelVersion] = IO.pure(entity)
          override def update(id: Long, entity: ModelVersion): IO[Int] = IO.pure(1)
          override def get(id: Long): IO[Option[ModelVersion]] = ???
          override def get(modelName: String, modelVersion: Long): IO[Option[ModelVersion]] = ???
          override def get(idx: Seq[Long]): IO[Seq[ModelVersion]] = ???
          override def delete(id: Long): IO[Int] = ???
          override def all(): IO[Seq[ModelVersion]] = ???
          override def listForModel(modelId: Long): IO[Seq[ModelVersion]] = ???
          override def lastModelVersionByModel(modelId: Long, max: Int): IO[Seq[ModelVersion]] = ???
        }

        val modelVersionMetadata = ModelVersionMetadata(
          modelName = model.name,
          contract = ModelContract.defaultInstance,
          profileTypes = Map.empty,
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

          override def push(dockerImage: DockerImage): IO[Unit] = {
              IO(p.success(dockerImage))
          }
        }
        val versionService = new ModelVersionService[IO] {
          override def getNextModelVersion(modelId: Long) = IO.pure(1)
          override def get(name: String, version: Long) = ???
          override def deleteVersions(mvs: Seq[ModelVersion]) = ???
          override def list = ???
          override def delete(versionId: Long) = ???
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
        val mh = new ModelPublisher[IO] {
          override def publish(t: DiscoveryEvent[ModelVersion, Long]): IO[Unit] = IO.unit
        }
        val bl = new BuildLoggingService[IO] {
          override def makeLogger(modelVersion: ModelVersion): IO[ProgressHandler] = IO(InfoProgressHandler)
          override def finishLogging(modelVersion: Long): IO[Option[Unit]] = IO(Some(()))
          override def getLogs(modelVersionId: Long, sinceLine: Int): IO[Option[fs2.Stream[IO, String]]] = IO(None)
        }
        val builder = ModelVersionBuilder[IO](
          imageBuilder = imageBuilder,
          modelVersionRepository = versionRepo,
          imageRepository = imageRepo,
          modelVersionService = versionService,
          storageOps = ops,
          modelDiscoveryHub = mh,
          buildLoggingService = bl
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
