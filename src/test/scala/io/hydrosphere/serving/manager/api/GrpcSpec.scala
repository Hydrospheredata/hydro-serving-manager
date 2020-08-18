package io.hydrosphere.serving.manager.api

import java.time.Instant

import cats.effect.IO
import com.google.protobuf.empty.Empty
import io.grpc.stub.StreamObserver
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.api.grpc.ManagerGrpcService
import io.hydrosphere.serving.manager.domain.deploy_config
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionService, ModelVersionStatus}
import io.hydrosphere.serving.manager.domain.servable.Servable.GenericServable
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableService}
import io.hydrosphere.serving.manager.grpc.entities.{ModelVersion => ProtoModelVersion}
import io.hydrosphere.serving.manager.util.DeferredResult

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.util.{Failure, Success}

class GrpcSpec extends GenericUnitTest {
  describe("Manager GRPC API") {
    it("should return ModelVersion for id") {
      val versionService = mock[ModelVersionService[IO]]
      when(versionService.get(1)).thenReturn(IO(
        ModelVersion.Internal(
          id = 1,
          image = DockerImage("test", "test"),
          created = Instant.now(),
          finished = None,
          modelVersion = 1,
          modelContract = ModelContract.defaultInstance,
          runtime = DockerImage("asd", "asd"),
          model = Model(1, "asd"),
          status = ModelVersionStatus.Assembling,
          installCommand = None,
          metadata = Map.empty
        )
      ))
      when(versionService.get(1000)).thenReturn(IO.raiseError(new IllegalArgumentException("1000")))
      val s = new ServableService[IO] {
        def all(): IO[List[Servable.GenericServable]] = ???
        def getFiltered(name: Option[String], versionId: Option[Long], metadata: Map[String,String]): IO[List[Servable.GenericServable]] = ???
        def stop(name: String): IO[GenericServable] = ???
        def get(name: String): IO[GenericServable] = ???
        def findAndDeploy(name: String, version: Long, deployConfigName: Option[String], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
        def findAndDeploy(modelId: Long, deployConfigName: Option[String], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
        def deploy(modelVersion: ModelVersion.Internal, deployConfig: Option[deploy_config.DeploymentConfiguration], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
      }
      val grpcApi = new ManagerGrpcService(versionService, s)

      grpcApi.getVersion(GetVersionRequest(1000)).onComplete {
        case Success(_) => fail("Value instead of exception")
        case Failure(exception) => assert(exception.isInstanceOf[IllegalArgumentException])
      }

      grpcApi.getVersion(GetVersionRequest(1)).map { mv =>
        assert(mv.id === 1)
      }
    }
    it("should return a stream of all ModelVersions") {
      val versionService = mock[ModelVersionService[IO]]
      when(versionService.all()).thenReturn(IO(List(
        ModelVersion.Internal(
          id = 1,
          image = DockerImage("test", "test"),
          created = Instant.now(),
          finished = None,
          modelVersion = 1,
          modelContract = ModelContract.defaultInstance,
          runtime = DockerImage("asd", "asd"),
          model = Model(1, "asd"),
          status = ModelVersionStatus.Assembling,
          installCommand = None,
          metadata = Map.empty
        ),
        ModelVersion.Internal(
          id = 2,
          image = DockerImage("test", "test"),
          created = Instant.now(),
          finished = None,
          modelVersion = 1,
          modelContract = ModelContract.defaultInstance,
          runtime = DockerImage("asd", "asd"),
          model = Model(1, "asd"),
          status = ModelVersionStatus.Assembling,
          installCommand = None,
          metadata = Map.empty
        )
      )))

      val buffer = ListBuffer.empty[ProtoModelVersion]
      var completionFlag = false
      val observer = new StreamObserver[ProtoModelVersion] {
        override def onNext(value: ProtoModelVersion): Unit = buffer += value
        override def onError(t: Throwable): Unit = ???
        override def onCompleted(): Unit = completionFlag = true
      }

      val s = new ServableService[IO] {
        def all(): IO[List[Servable.GenericServable]] = ???
        def getFiltered(name: Option[String], versionId: Option[Long], metadata: Map[String,String]): IO[List[Servable.GenericServable]] = ???
        def stop(name: String): IO[GenericServable] = ???
        def get(name: String): IO[GenericServable] = ???
        def findAndDeploy(name: String, version: Long, deployConfigName: Option[String], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
        def findAndDeploy(modelId: Long, deployConfigName: Option[String], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
        def deploy(modelVersion: ModelVersion.Internal, deployConfig: Option[deploy_config.DeploymentConfiguration], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
      }
      val grpcApi = new ManagerGrpcService(versionService, s)
      grpcApi.getAllVersions(Empty(), observer)

      Future {
        assert(buffer.map(_.id) === Seq(1, 2))
        assert(completionFlag)
      }
    }
    it("should handle ModelVersion stream error") {
      val versionRepo = mock[ModelVersionService[IO]]
      when(versionRepo.all()).thenReturn(IO.raiseError(new IllegalStateException("AAAAAAA")))

      val errors = ListBuffer.empty[Throwable]
      val observer = new StreamObserver[ProtoModelVersion] {
        override def onNext(value: ProtoModelVersion): Unit = ???
        override def onError(t: Throwable): Unit = errors += t
        override def onCompleted(): Unit = ???
      }
      val s = new ServableService[IO] {
        def all(): IO[List[Servable.GenericServable]] = ???
        def getFiltered(name: Option[String], versionId: Option[Long], metadata: Map[String,String]): IO[List[Servable.GenericServable]] = ???
        def stop(name: String): IO[GenericServable] = ???
        def get(name: String): IO[GenericServable] = ???
        def findAndDeploy(name: String, version: Long, deployConfigName: Option[String], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
        def findAndDeploy(modelId: Long, deployConfigName: Option[String], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
        def deploy(modelVersion: ModelVersion.Internal, deployConfig: Option[deploy_config.DeploymentConfiguration], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
      }
      val grpcApi = new ManagerGrpcService(versionRepo, s)
      grpcApi.getAllVersions(Empty(), observer)
      Future {
        assert(errors.nonEmpty)
      }
    }
  }
}