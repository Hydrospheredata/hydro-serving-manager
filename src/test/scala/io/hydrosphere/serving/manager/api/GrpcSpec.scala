package io.hydrosphere.serving.manager.api

import cats.effect.IO
import cats.effect.std.Dispatcher
import com.google.protobuf.empty.Empty
import io.grpc.stub.StreamObserver
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.api.grpc.ManagerGrpcService
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.{
  ModelVersion,
  ModelVersionService,
  ModelVersionStatus
}
import io.hydrosphere.serving.manager.domain.servable.ServableService
import io.hydrosphere.serving.proto.manager.api.GetVersionRequest
import io.hydrosphere.serving.proto.manager.entities.{ModelVersion => ProtoModelVersion}

import java.time.Instant
import scala.collection.mutable.ListBuffer
import scala.concurrent.Promise
import scala.concurrent.duration._

class GrpcSpec extends GenericUnitTest {
  describe("Manager GRPC API") {
    it("should return ModelVersion for id") {
      ioAssert {
        val versionService = mock[ModelVersionService[IO]]
        when(versionService.get(1)).thenReturn(
          IO(
            ModelVersion.Internal(
              id = 1,
              image = DockerImage("test", "test"),
              created = Instant.now(),
              finished = None,
              modelVersion = 1,
              modelSignature = Signature.defaultSignature,
              runtime = DockerImage("asd", "asd"),
              model = Model(1, "asd"),
              status = ModelVersionStatus.Assembling,
              installCommand = None,
              metadata = Map.empty
            )
          )
        )
        when(versionService.get(1000))
          .thenReturn(IO.raiseError(new IllegalArgumentException("1000")))
        val s = mock[ServableService[IO]]
        Dispatcher[IO].use { disp =>
          val grpcApi = new ManagerGrpcService[IO](versionService, s, disp)

          IO.fromFuture(IO(grpcApi.getVersion(GetVersionRequest(1000))))
            .attempt
            .flatMap {
              case Right(_)    => IO(fail("Value instead of exception"))
              case Left(value) => IO(assert(value.isInstanceOf[IllegalArgumentException]))
            }
            .flatMap { _ =>
              IO.fromFuture(IO(grpcApi.getVersion(GetVersionRequest(1))))
                .map(mv => assert(mv.id == 1))
            }
        }
      }
    }

    it("should return a stream of all ModelVersions") {
      val versionService = mock[ModelVersionService[IO]]
      when(versionService.all()).thenReturn(
        IO(
          List(
            ModelVersion.Internal(
              id = 1,
              image = DockerImage("test", "test"),
              created = Instant.now(),
              finished = None,
              modelVersion = 1,
              modelSignature = Signature.defaultSignature,
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
              modelSignature = Signature.defaultSignature,
              runtime = DockerImage("asd", "asd"),
              model = Model(1, "asd"),
              status = ModelVersionStatus.Assembling,
              installCommand = None,
              metadata = Map.empty
            )
          )
        )
      )

      val buffer         = ListBuffer.empty[ProtoModelVersion]
      var completionFlag = false
      val observer = new StreamObserver[ProtoModelVersion] {
        override def onNext(value: ProtoModelVersion): Unit = buffer += value

        override def onError(t: Throwable): Unit = ???

        override def onCompleted(): Unit = completionFlag = true
      }

      val s = mock[ServableService[IO]]
      Dispatcher[IO]
        .use { disp =>
          IO {
            val grpcApi = new ManagerGrpcService[IO](versionService, s, disp)
            grpcApi.getAllVersions(Empty(), observer)
          }
        }
        .unsafeRunSync()
      assert(buffer.map(_.id) === Seq(1, 2))
      assert(completionFlag)
    }

    it("should handle ModelVersion stream error") {
      val versionRepo = mock[ModelVersionService[IO]]
      when(versionRepo.all()).thenReturn(IO.raiseError(new IllegalStateException("AAAAAAA")))

      val error = Promise[Throwable]()
      val observer = new StreamObserver[ProtoModelVersion] {
        override def onNext(value: ProtoModelVersion): Unit = ???

        override def onError(t: Throwable): Unit =
          error.success(t)

        override def onCompleted(): Unit = ???
      }
      val s = mock[ServableService[IO]]
      Dispatcher[IO]
        .use { disp =>
          val grpcApi = new ManagerGrpcService[IO](versionRepo, s, disp)
          IO(grpcApi.getAllVersions(Empty(), observer))
        }
        .unsafeRunSync()

      assert(error.isCompleted)
    }
  }
}
