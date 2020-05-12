package io.hydrosphere.serving.manager.api

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import com.google.protobuf.empty.Empty
import io.grpc.stub.StreamObserver
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.api.grpc.ManagerGrpcService
import io.hydrosphere.serving.manager.domain.contract._
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version._
import io.hydrosphere.serving.manager.domain.servable.ServableService
import io.hydrosphere.serving.manager.grpc.entities.{ModelVersion => ProtoModelVersion}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.util.{Failure, Success}

class GrpcSpec extends GenericUnitTest {
  describe("Manager GRPC API") {

    val modelVersion1 = ModelVersion.Internal(
      id = 1,
      image = DockerImage("test", "test"),
      created = Instant.now(),
      finished = None,
      modelVersion = 1,
      modelContract = Contract(
        Signature(
          "test",
          NonEmptyList.of(
            Field.Tensor("input", DataType.DT_INT32, TensorShape.Dynamic, None)
          ),
          NonEmptyList.of(
            Field.Tensor("output", DataType.DT_INT32, TensorShape.Dynamic, None)
          )
        )
      ),
      runtime = DockerImage("asd", "asd"),
      model = Model(1, "asd"),
      hostSelector = None,
      status = ModelVersionStatus.Assembling,
      installCommand = None,
      metadata = Map.empty
    )
    it("should return ModelVersion for id") {
      val versionService = mock[ModelVersionService[IO]]
      when(versionService.get(1)).thenReturn(IO.pure(modelVersion1))
      when(versionService.get(1000)).thenReturn(IO.raiseError(new IllegalArgumentException("1000")))
      val servableService = mock[ServableService[IO]]

      val grpcApi = new ManagerGrpcService(versionService, servableService)

      grpcApi.getVersion(GetVersionRequest(1000)).onComplete {
        case Success(_)         => fail("Value instead of exception")
        case Failure(exception) => assert(exception.isInstanceOf[IllegalArgumentException])
      }

      grpcApi.getVersion(GetVersionRequest(1)).map(mv => assert(mv.id === 1))
    }
    it("should return a stream of all ModelVersions") {
      val versionService = mock[ModelVersionService[IO]]
      when(versionService.all()).thenReturn(IO(List(modelVersion1, modelVersion1)))

      val buffer         = ListBuffer.empty[ProtoModelVersion]
      var completionFlag = false
      val observer = new StreamObserver[ProtoModelVersion] {
        override def onNext(value: ProtoModelVersion): Unit = buffer += value
        override def onError(t: Throwable): Unit            = ???
        override def onCompleted(): Unit                    = completionFlag = true
      }

      val s       = mock[ServableService[IO]]
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
        override def onError(t: Throwable): Unit            = errors += t
        override def onCompleted(): Unit                    = ???
      }
      val s       = mock[ServableService[IO]]
      val grpcApi = new ManagerGrpcService(versionRepo, s)
      grpcApi.getAllVersions(Empty(), observer)
      Future {
        assert(errors.nonEmpty)
      }
    }
  }
}
