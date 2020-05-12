package io.hydrosphere.serving.manager.domain.application

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.concurrent.Deferred
import cats.implicits._
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.application.requests._
import io.hydrosphere.serving.manager.domain.contract._
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version._
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableGC, ServableService}
import io.hydrosphere.serving.manager.util.DeferredResult

import scala.collection.mutable.ListBuffer

class ApplicationServiceSpec extends GenericUnitTest {
  val signature = Signature(
    "claim",
    NonEmptyList.of(
      Field.Tensor("in", DataType.DT_DOUBLE, TensorShape.Dynamic)
    ),
    NonEmptyList.of(
      Field.Tensor("out", DataType.DT_DOUBLE, TensorShape.Dynamic)
    )
  )
  val contract = Contract(signature)
  val modelVersion = ModelVersion.Internal(
    id = 1,
    image = DockerImage("test", "t"),
    created = Instant.now(),
    finished = None,
    modelVersion = 1,
    modelContract = contract,
    runtime = DockerImage("runtime", "v"),
    model = Model(1, "model"),
    hostSelector = None,
    status = ModelVersionStatus.Released,
    installCommand = None,
    metadata = Map.empty
  )
  val externalMv = ModelVersion.External(
    id = 1,
    created = Instant.now(),
    modelVersion = 1,
    modelContract = contract,
    model = Model(1, "model"),
    metadata = Map.empty
  )

  val assemblingApp = Application(
    id = 1,
    name = "test",
    namespace = None,
    status = Application.Status.Assembling,
    kafkaStreaming = List.empty,
    executionGraph = singleModelGraph(modelVersion),
    message = "ok"
  )

  describe("Application Deployer") {

    it("should reject application with external ModelVersion") {

      val appRepo = mock[ApplicationRepository[IO]]
      when(appRepo.get("test")).thenReturn(IO(None))

      val versionRepo = mock[ModelVersionRepository[IO]]
      when(versionRepo.get(1)).thenReturn(externalMv.some.pure[IO])
      val servableService = mock[ServableService[IO]]

      val appDeployer = ApplicationDeployer.default[IO](
        applicationRepository = appRepo,
        versionRepository = versionRepo,
        servableService = servableService,
        discoveryHub = noopPublisher
      )
      val graph = ExecutionGraphRequest(
        NonEmptyList.of(
          PipelineStageRequest(
            NonEmptyList.of(
              ModelVariantRequest(
                modelVersionId = 1,
                weight = 100
              )
            )
          )
        )
      )
      val result = appDeployer.deploy("test", graph, List.empty).attempt.unsafeRunSync()
      result.left.value.getClass should be(classOf[DomainError.InvalidRequest])
    }

    it("should start application build") {
      ioAssert {
        val appRepo = mock[ApplicationRepository[IO]]
        when(appRepo.get("test")).thenReturn(None.pure[IO])
        when(appRepo.create(any)).thenReturn(assemblingApp.pure[IO])
        val versionRepo = mock[ModelVersionRepository[IO]]
        when(versionRepo.get(1)).thenReturn(modelVersion.some.pure[IO])
        val servableService = mock[ServableService[IO]]
        when(servableService.deploy(any, any))
          .thenAnswer[ModelVersion.Internal, Map[String, String]] {
            case (modelVersion, metadata) =>
              val s = Servable(
                modelVersion,
                "hi",
                Servable.Status.Serving,
                Nil,
                "Ok",
                "host".some,
                9090.some,
                metadata
              )
              DeferredResult.completed(s)
          }

        val appDeployer = ApplicationDeployer.default[IO](
          applicationRepository = appRepo,
          versionRepository = versionRepo,
          servableService = servableService,
          discoveryHub = noopPublisher
        )
        val graph = ExecutionGraphRequest(
          NonEmptyList.of(
            PipelineStageRequest(
              NonEmptyList.of(
                ModelVariantRequest(
                  modelVersionId = 1,
                  weight = 100
                )
              )
            )
          )
        )
        appDeployer.deploy("test", graph, List.empty).map { res =>
          assert(res.started.name == "test")
          assert(res.started.status === Application.Status.Assembling)
        // build will fail nonetheless
        }
      }
    }

    it("should handle failed application builds") {
      ioAssert {
        val appRepo = mock[ApplicationRepository[IO]]
        when(appRepo.update(any)).thenReturn(1.pure[IO])
        when(appRepo.get("test")).thenReturn(None.pure[IO])
        when(appRepo.create(any)).thenReturn(assemblingApp.pure[IO])

        val versionRepo = mock[ModelVersionRepository[IO]]
        when(versionRepo.get(1)).thenReturn(modelVersion.some.pure[IO])
        val servableService = mock[ServableService[IO]]
        when(servableService.deploy(any, any))
          .thenAnswer(IO.raiseError(new RuntimeException("Test error")))

        val appDeployer = ApplicationDeployer.default[IO](
          applicationRepository = appRepo,
          versionRepository = versionRepo,
          servableService = servableService,
          discoveryHub = noopPublisher
        )
        val graph = ExecutionGraphRequest(
          NonEmptyList.of(
            PipelineStageRequest(
              NonEmptyList.of(
                ModelVariantRequest(
                  modelVersionId = 1,
                  weight = 100
                )
              )
            )
          )
        )
        appDeployer.deploy("test", graph, List.empty).flatMap { res =>
          println("Waiting for build")
          res.completed.get.map { x =>
            assert(x.status === Application.Status.Failed)
            assert(x.message == "Test error")
          }
        }
      }
    }

    it("should handle finished builds") {
      ioAssert {
        val appRepo = mock[ApplicationRepository[IO]]
        when(appRepo.update(any)).thenReturn(1.pure[IO])
        when(appRepo.get("test")).thenReturn(None.pure[IO])
        when(appRepo.create(any)).thenReturn(assemblingApp.pure[IO])

        val versionRepo = mock[ModelVersionRepository[IO]]
        when(versionRepo.get(1)).thenReturn(modelVersion.some.pure[IO])
        val servableService = mock[ServableService[IO]]
        when(servableService.deploy(any, any))
          .thenAnswer[ModelVersion.Internal, Map[String, String]] {
            case (mv, metadata) =>
              val s = Servable(
                modelVersion = mv,
                nameSuffix = "test",
                status = Servable.Status.Serving,
                usedApps = Nil,
                message = "Ok",
                host = "host".some,
                port = 9090.some,
                metadata = metadata
              )
              DeferredResult.completed(s)
          }

        val appChanged   = ListBuffer.empty[Application]
        val discoveryHub = mock[ApplicationEvents.Publisher[IO]]
        when(discoveryHub.update(any)).thenAnswer[Application](app => IO(appChanged += app))

        val graph = ExecutionGraphRequest(
          NonEmptyList.of(
            PipelineStageRequest(
              NonEmptyList.of(
                ModelVariantRequest(
                  modelVersionId = 1,
                  weight = 100
                )
              )
            )
          )
        )
        val appDeployer = ApplicationDeployer.default[IO](
          applicationRepository = appRepo,
          versionRepository = versionRepo,
          servableService = servableService,
          discoveryHub = discoveryHub
        )
        appDeployer.deploy("test", graph, List.empty).flatMap { res =>
          res.completed.get.map { finished =>
            assert(finished.name == "test")
            assert(finished.status === Application.Status.Ready)
            assert(appChanged.toList.nonEmpty)
          }
        }
      }
    }
  }

  describe("Application management service") {

    it("should rebuild on update") {
      ioAssert {
        val d       = Deferred.unsafe[IO, Unit]
        val appRepo = mock[ApplicationRepository[IO]]
        when(appRepo.get(anyLong)).thenReturn(assemblingApp.some.pure[IO])
        when(appRepo.get(any[String])).thenReturn(assemblingApp.some.pure[IO])
        when(appRepo.delete(any)).thenReturn(d.complete(()).as(1))

        val versionRepo = mock[ModelVersionRepository[IO]]

        val servableService = mock[ServableService[IO]]

        val apps           = ListBuffer.empty[Application]
        val eventPublisher = mock[ApplicationEvents.Publisher[IO]]
        when(eventPublisher.remove(any)).thenAnswer[String] { app =>
          IO(apps.filterNot(_.name == app))
        }

        val graph = ExecutionGraphRequest(
          NonEmptyList.of(
            PipelineStageRequest(
              NonEmptyList.of(
                ModelVariantRequest(
                  modelVersionId = 1,
                  weight = 100
                )
              )
            )
          )
        )
        val appDep = mock[ApplicationDeployer[IO]]
        when(appDep.deploy(any, any, any)).thenReturn(DeferredResult.completed(assemblingApp))

        val gc = ServableGC.noop[IO]()
        val applicationService =
          ApplicationService[IO](appRepo, versionRepo, servableService, eventPublisher, appDep, gc)
        val updateReq = UpdateApplicationRequest(1, "test", None, graph, Option.empty, Option.empty)
        applicationService.update(updateReq).map { res =>
          assert(res.started.name == "test")
          assert(res.started.status === Application.Status.Assembling)
        }
      }
    }
  }
}
