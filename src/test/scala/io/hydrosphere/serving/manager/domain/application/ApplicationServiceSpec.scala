package io.hydrosphere.serving.manager.domain.application

import java.time.LocalDateTime

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.concurrent.Deferred
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.contract.model_field.ModelField
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.discovery.application.{ApplicationDiscoveryHub, ApplicationEvent}
import io.hydrosphere.serving.manager.domain.application.graph.VersionGraphComposer.PipelineStage
import io.hydrosphere.serving.manager.domain.application.graph.{Variant, VersionGraphComposer}
import io.hydrosphere.serving.manager.domain.application.requests._
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionRepository, ModelVersionStatus}
import io.hydrosphere.serving.manager.domain.servable.Servable._
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableService}
import io.hydrosphere.serving.manager.grpc.entities.ServingApp
import io.hydrosphere.serving.manager.util.DeferredResult
import io.hydrosphere.serving.tensorflow.types.DataType
import org.mockito.Matchers

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext

class ApplicationServiceSpec extends GenericUnitTest {
  val signature = ModelSignature(
    "claim",
    Seq(ModelField("in", None, typeOrSubfields = ModelField.TypeOrSubfields.Dtype(DataType.DT_DOUBLE))),
    Seq(ModelField("out", None, typeOrSubfields = ModelField.TypeOrSubfields.Dtype(DataType.DT_DOUBLE)))
  )
  val contract = ModelContract("", Some(signature))
  val modelVersion = ModelVersion(
    id = 1,
    image = DockerImage("test", "t"),
    created = LocalDateTime.now(),
    finished = None,
    modelVersion = 1,
    modelContract = contract,
    runtime = DockerImage("runtime", "v"),
    model = Model(1, "model"),
    hostSelector = None,
    status = ModelVersionStatus.Released,
    profileTypes = Map.empty,
    installCommand = None,
    metadata = Map.empty
  )

  describe("Application management service") {
    implicit val cs = IO.contextShift(ExecutionContext.global)
    it("should start application build") {
      ioAssert {
        val appRepo = mock[ApplicationRepository[IO]]
        when(appRepo.get("test")).thenReturn(IO(None))
        when(appRepo.create(Matchers.any())).thenReturn(IO(
          Application(
            id = 1,
            name = "test",
            namespace = None,
            status = Application.Assembling(
              NonEmptyList.of(PipelineStage(
                NonEmptyList.of(Variant(modelVersion, 100)),
                modelVersion.modelContract.predict.get
              ))
            ),
            signature = signature.copy(signatureName = "test"),
            kafkaStreaming = List.empty
          )
        ))
        val versionRepo = mock[ModelVersionRepository[IO]]
        when(versionRepo.get(1)).thenReturn(IO(Some(modelVersion)))
        when(versionRepo.get(Seq(1L))).thenReturn(IO(Seq(modelVersion)))
        val servableService = new ServableService[IO] {
          override def deploy(modelVersion: ModelVersion): IO[DeferredResult[IO, GenericServable]] = {
            IO.pure {
              val s = Servable(modelVersion, "hi", Servable.Serving("Ok", "host", 9090))
              val d = Deferred[IO, GenericServable].unsafeRunSync()
              d.complete(s).unsafeRunSync()
              DeferredResult(s, d)
            }
          }

          override def stop(name: String): IO[GenericServable] = ???

          override def findAndDeploy(name: String, version: Long): IO[DeferredResult[IO, GenericServable]] = ???
        }
        val graphComposer = VersionGraphComposer.default
        val discoveryHub = mock[ApplicationDiscoveryHub[IO]]
        val applicationService = ApplicationService[IO](
          appRepo,
          versionRepo,
          servableService,
          discoveryHub,
          graphComposer
        )
        val graph = ExecutionGraphRequest(NonEmptyList.of(
          PipelineStageRequest(NonEmptyList.of(
            ModelVariantRequest(
              modelVersionId = 1,
              weight = 100
            )
          ))
        ))
        val createReq = CreateApplicationRequest("test", None, graph, Option.empty)
        applicationService.create(createReq).map { res =>
          assert(res.started.name === "test")
          assert(res.started.status.isInstanceOf[Application.Assembling])
          // build will fail nonetheless
        }
      }
    }

    it("should handle failed application builds") {
      ioAssert {
        val appRepo = mock[ApplicationRepository[IO]]
        when(appRepo.update(Matchers.any())).thenReturn(IO.pure(1))
        when(appRepo.get("test")).thenReturn(IO(None))
        when(appRepo.create(Matchers.any())).thenReturn(IO(
          Application(
            id = 1,
            name = "test",
            namespace = None,
            status = Application.Assembling(NonEmptyList.of(
               PipelineStage(NonEmptyList.of(
                 Variant(modelVersion, 100)),
                 signature
               )
            )),
            signature = signature.copy(signatureName = "test"),
            kafkaStreaming = List.empty
          )
        ))
        val versionRepo = mock[ModelVersionRepository[IO]]
        when(versionRepo.get(1)).thenReturn(IO(Some(modelVersion)))
        val servableService = new ServableService[IO] {
          override def deploy(mv: ModelVersion): IO[Nothing] = {
            IO.raiseError(new RuntimeException("Test error"))
          }
          override def findAndDeploy(name: String, version: Long): IO[DeferredResult[IO, GenericServable]] = ???
          override def stop(name: String): IO[GenericServable] = ???
        }

        val graphComposer = VersionGraphComposer.default
        val discoveryHub = mock[ApplicationDiscoveryHub[IO]]
        val applicationService = ApplicationService[IO](
          appRepo,
          versionRepo,
          servableService,
          discoveryHub,
          graphComposer
        )
        val graph = ExecutionGraphRequest(NonEmptyList.of(
          PipelineStageRequest(NonEmptyList.of(
            ModelVariantRequest(
              modelVersionId = 1,
              weight = 100
            )
          ))
        ))
        val createReq = CreateApplicationRequest("test", None, graph, Option.empty)
        applicationService.create(createReq).flatMap { res =>
          println("Waiting for build")
          res.completed.get.map { x =>
            val status = x.status.asInstanceOf[Application.Failed]
            assert(status.reason.get === "Test error")
          }
        }
      }
    }

    it("should handle finished builds") {
      ioAssert {
        val appRepo = mock[ApplicationRepository[IO]]
        when(appRepo.update(Matchers.any())).thenReturn(IO(1))
        when(appRepo.get("test")).thenReturn(IO(None))
        when(appRepo.create(Matchers.any())).thenReturn(IO(
          Application(
            id = 1,
            name = "test",
            namespace = None,
            status = Application.Assembling(
              NonEmptyList.of(PipelineStage(
                NonEmptyList.of(Variant(modelVersion, 100)),
                modelVersion.modelContract.predict.get
              ))
            ),
            signature = signature.copy(signatureName = "test"),
            kafkaStreaming = List.empty
          )
        ))
        val versionRepo = mock[ModelVersionRepository[IO]]
        when(versionRepo.get(1)).thenReturn(IO(Some(modelVersion)))
        when(versionRepo.get(Seq(1L))).thenReturn(IO(Seq(modelVersion)))
        val servableService = new ServableService[IO] {
          override def deploy(mv: ModelVersion) = IO.pure {
            val s = Servable(
              modelVersion = mv,
              nameSuffix = "test",
              status = Servable.Serving("Ok", "host", 9090)
            )
            val d = Deferred[IO, GenericServable].unsafeRunSync()
            d.complete(s).unsafeRunSync()
            DeferredResult(s, d)
          }

          override def findAndDeploy(name: String, version: Long) = ???

          override def stop(name: String): IO[GenericServable] = ???
        }

        val appChanged = ListBuffer.empty[ServingApp]
        val discoveryHub = new ApplicationDiscoveryHub[IO] {
          override def update(e: ApplicationEvent): IO[Unit] = e match {
            case ApplicationEvent.Started(app) => IO(appChanged += app)
            case _ => IO.unit
          }

          override def current: IO[List[ServingApp]] = IO.pure(appChanged.toList)
        }
        val graph = ExecutionGraphRequest(NonEmptyList.of(
          PipelineStageRequest(NonEmptyList.of(
            ModelVariantRequest(
              modelVersionId = 1,
              weight = 100
            )
          ))
        ))
        val graphComposer = VersionGraphComposer.default
        val applicationService = ApplicationService[IO](
          appRepo,
          versionRepo,
          servableService,
          discoveryHub,
          graphComposer
        )
        val createReq = CreateApplicationRequest("test", None, graph, Option.empty)
        applicationService.create(createReq).flatMap { res =>
          res.completed.get.map { finished =>
            assert(finished.name === "test")
            assert(finished.status.isInstanceOf[Application.Ready])
            assert(appChanged.nonEmpty)
          }
        }
      }
    }

    it("should rebuild on update") {
      ioAssert {
        val ogApp = Application(
          id = 1,
          name = "test",
          namespace = None,
          signature = signature.copy(signatureName = "test"),
          status = Application.Assembling(
            NonEmptyList.of(PipelineStage(
              NonEmptyList.of(Variant(modelVersion, 100)),
              modelVersion.modelContract.predict.get
            ))
          ),
          kafkaStreaming = List.empty
        )
        var app = Option(ogApp)
        val appRepo = mock[ApplicationRepository[IO]]
        when(appRepo.get(Matchers.anyLong())).thenReturn(IO(app))
        when(appRepo.get(Matchers.anyString())).thenReturn(IO(app))
        when(appRepo.create(Matchers.any())).thenReturn(IO(ogApp))
        when(appRepo.applicationsWithCommonServices(Matchers.any(), Matchers.any())).thenReturn(IO(List.empty))
        when(appRepo.update(Matchers.any())).thenReturn(IO(1))
        when(appRepo.delete(Matchers.any())).thenReturn(IO{
          app = None
          1
        })

        val versionRepo = mock[ModelVersionRepository[IO]]
        when(versionRepo.get(Matchers.any[Long]())).thenReturn(IO(Some(modelVersion)))
        when(versionRepo.get(Matchers.any[Seq[Long]]())).thenReturn(IO(Seq(modelVersion)))

        val servableService = new ServableService[IO] {
          override def deploy(mv: ModelVersion) = {
            IO.pure {
              val s = Servable(mv, "test", Servable.Serving("Ok", "host", 9090))
              val d = Deferred[IO, GenericServable].unsafeRunSync()
              d.complete(s).unsafeRunSync()
              DeferredResult(s, d)
            }
          }

          override def findAndDeploy(name: String, version: Long) = ???

          override def stop(name: String): IO[GenericServable] = ???
        }

        val apps = ListBuffer.empty[ServingApp]
        val eventPublisher = new ApplicationDiscoveryHub[IO] {
          override def update(e: ApplicationEvent): IO[Unit] = e match {
            case ApplicationEvent.Started(app) => IO(apps += app)
            case ApplicationEvent.Removed(id) => IO(apps --= apps.filter(_.id == id.toString))
          }

          override def current: IO[List[ServingApp]] = IO.pure(apps.toList)
        }
        val graph = ExecutionGraphRequest(NonEmptyList.of(
          PipelineStageRequest(NonEmptyList.of(
            ModelVariantRequest(
              modelVersionId = 1,
              weight = 100
            )
          ))
        ))
        val graphComposer = VersionGraphComposer.default
        val applicationService = ApplicationService[IO](
          appRepo,
          versionRepo,
          servableService,
          eventPublisher,
          graphComposer
        )
        val updateReq = UpdateApplicationRequest(1, "test", None, graph, Option.empty)
        applicationService.update(updateReq).map { res =>
          assert(res.started.name === "test")
          assert(res.started.status.isInstanceOf[Application.Assembling])
        }
      }
    }
  }
}