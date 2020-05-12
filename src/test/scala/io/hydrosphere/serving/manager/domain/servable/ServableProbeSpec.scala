package io.hydrosphere.serving.manager.domain.servable

import java.time.Instant

import cats.effect.IO
import cats.implicits._
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.clouddriver.{CloudDriver, CloudInstance}
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionStatus}
import io.hydrosphere.serving.tensorflow.api.prediction_service.StatusResponse

class ServableProbeSpec extends GenericUnitTest {
  val externalMv: ModelVersion.External = ModelVersion.External(
    id = 1,
    created = Instant.now(),
    modelVersion = 1,
    modelContract = defaultContract,
    model = Model(1, "name"),
    metadata = Map.empty
  )
  val mv: ModelVersion.Internal = ModelVersion.Internal(
    id = 10,
    image = DockerImage("name", "tag"),
    created = Instant.now(),
    finished = None,
    modelVersion = 1,
    modelContract = defaultContract,
    runtime = DockerImage("runtime", "tag"),
    model = Model(1, "name"),
    hostSelector = None,
    status = ModelVersionStatus.Released,
    installCommand = None,
    metadata = Map.empty
  )
  val servable: Servable =
    Servable(mv, "test", Servable.Status.Starting, Nil, "starting", None, None)

  describe("Servable probe") {
    it("should succed on good status and ping") {
      val driver = mock[CloudDriver[IO]]
      when(driver.instance("name-1-test")).thenReturn(
        IO {
          println("Instance is ready")
          CloudInstance(
            mv.id,
            Servable.fullName(mv.model.name, mv.modelVersion, "test"),
            CloudInstance.Status.Running("node.cluster.domain", 9090)
          ).some
        }
      )

      val clientCtor =
        mockPredictionFactory(IO(StatusResponse(StatusResponse.ServiceStatus.SERVING, "Ok")))

      val probe = ServableProbe.default[IO](clientCtor, driver)
      val res   = probe.probe(servable).unsafeRunSync()
      assert(res.getStatus == Servable.Status.Serving)
    }

    it("should fail on non-serving status") {
      val clientCtor = mockPredictionFactory(
        IO(
          StatusResponse(
            status = StatusResponse.ServiceStatus.NOT_SERVING,
            message = "WTF"
          )
        )
      )

      val driver = mock[CloudDriver[IO]]
      when(driver.instance("name-1-test")).thenReturn(
        IO {
          println("Instance is ready")
          CloudInstance(
            mv.id,
            Servable.fullName(mv.model.name, mv.modelVersion, "test"),
            CloudInstance.Status.Running("node.cluster.domain", 9090)
          ).some
        }
      )
      val probe  = ServableProbe.default[IO](clientCtor, driver)
      val result = probe.probe(servable).unsafeRunSync()
      assert(result.getStatus == Servable.Status.NotServing)
    }

    it("should propagate GRPC errors") {
      val clientCtor = mockPredictionFactory(IO.raiseError(new RuntimeException("GRPC test error")))

      val driver = mock[CloudDriver[IO]]
      when(driver.instance("name-1-test")).thenReturn(
        IO {
          println("Instance is ready")
          CloudInstance(
            mv.id,
            Servable.fullName(mv.model.name, mv.modelVersion, "test"),
            CloudInstance.Status.Running("node.cluster.domain", 9090)
          ).some
        }
      )
      val probe  = ServableProbe.default[IO](clientCtor, driver)
      val result = probe.probe(servable).unsafeRunSync()

      assert(result.getStatus === Servable.Status.NotServing)
      assert(result.message == "Ping error: GRPC test error")
      assert(result.getHost.contains("node.cluster.domain"))
      assert(result.getPort.contains(9090))
    }
  }
}
