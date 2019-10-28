package io.hydrosphere.serving.manager.domain.application

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.concurrent.Deferred
import cats.effect.{Concurrent, IO}
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.contract.model_field.ModelField
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.discovery.{DiscoveryEvent}
import io.hydrosphere.serving.manager.domain.application.Application.GenericApplication
import io.hydrosphere.serving.manager.domain.application.graph.VersionGraphComposer.PipelineStage
import io.hydrosphere.serving.manager.domain.application.graph.{Variant, VersionGraphComposer}
import io.hydrosphere.serving.manager.domain.application.requests._
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionRepository, ModelVersionStatus}
import io.hydrosphere.serving.manager.domain.servable.Servable._
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableService}
import io.hydrosphere.serving.manager.util.DeferredResult
import io.hydrosphere.serving.tensorflow.types.DataType
import org.mockito.Matchers

import scala.collection.mutable.ListBuffer

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
  describe("Application Deployer") {

    it("should start application build") {
      ioAssert {
        val appRepo = mock[ApplicationRepository[IO]]
        when(appRepo.get("test")).thenReturn(IO(None))
        when(appRepo.create(Matchers.any())).thenReturn(IO(
          Application(
            id = 1,
            name = "test",
            namespace = None,
            status = Application.Assembling,
            signature = signature.copy(signatureName = "test"),
            kafkaStreaming = List.empty,
            versionGraph = NonEmptyList.of(PipelineStage(
              NonEmptyList.of(Variant(modelVersion, 100)),
              modelVersion.modelContract.predict.get
            ))
          )
        ))
        val versionRepo = mock[ModelVersionRepository[IO]]
        when(versionRepo.get(1)).thenReturn(IO(Some(modelVersion)))
        val servableService = new ServableService[IO] {
          def all(): IO[List[Servable.GenericServable]] = ???
          def getFiltered(name: Option[String], versionId: Option[Long], metadata: Map[String,String]): IO[List[Servable.GenericServable]] = ???
          def deploy(modelVersion: ModelVersion, metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = {
            IO.pure {
              val s = Servable(modelVersion, "hi", Servable.Serving("Ok", "host", 9090), Nil)
              val d = Deferred[IO, GenericServable].unsafeRunSync()
              d.complete(s).unsafeRunSync()
              DeferredResult(s, d)
            }
          }
          def stop(name: String): IO[GenericServable] = ???
          def findAndDeploy(name: String, version: Long, metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
          def findAndDeploy(modelId: Long, metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
          def get(name: String): IO[GenericServable] = ???
        }
        val graphComposer = VersionGraphComposer.default
        val discoveryHub = new ApplicationEvents.Publisher[IO] {
          override def publish(t: DiscoveryEvent[GenericApplication, String]): IO[Unit] = IO.unit
        }
        val appDeployer = ApplicationDeployer.default[IO]()(
          Concurrent[IO],
          applicationRepository = appRepo,
          versionRepository = versionRepo,
          servableService= servableService,
          discoveryHub = discoveryHub,
          graphComposer = graphComposer
        )
        val graph = ExecutionGraphRequest(NonEmptyList.of(
          PipelineStageRequest(NonEmptyList.of(
            ModelVariantRequest(
              modelVersionId = 1,
              weight = 100
            )
          ))
        ))
        appDeployer.deploy("test", graph, List.empty).map { res =>
          assert(res.started.name === "test")
          assert(res.started.status.isInstanceOf[Application.Assembling.type])
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
            status = Application.Assembling,
            signature = signature.copy(signatureName = "test"),
            kafkaStreaming = List.empty,
            versionGraph = NonEmptyList.of(
              PipelineStage(NonEmptyList.of(
                Variant(modelVersion, 100)),
                signature
              )
            )
          )
        ))
        val versionRepo = mock[ModelVersionRepository[IO]]
        when(versionRepo.get(1)).thenReturn(IO(Some(modelVersion)))
        val servableService = new ServableService[IO] {
          def all(): IO[List[Servable.GenericServable]] = ???
          def getFiltered(name: Option[String], versionId: Option[Long], metadata: Map[String,String]): IO[List[Servable.GenericServable]] = ???
          def deploy(mv: ModelVersion, metadata: Map[String, String]): IO[Nothing] = {
            IO.raiseError(new RuntimeException("Test error"))
          }
          def findAndDeploy(name: String, version: Long, metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
          def stop(name: String): IO[GenericServable] = ???
          def findAndDeploy(modelId: Long, metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
          def get(name: String): IO[GenericServable] = ???
        }
        val graphComposer = VersionGraphComposer.default
        val discoveryHub = new ApplicationEvents.Publisher[IO] {
          override def publish(t: DiscoveryEvent[GenericApplication, String]): IO[Unit] = IO.unit
        }
        val appDeployer = ApplicationDeployer.default[IO]()(
          Concurrent[IO],
          applicationRepository = appRepo,
          versionRepository = versionRepo,
          servableService= servableService,
          discoveryHub = discoveryHub,
          graphComposer = graphComposer
        )
        val graph = ExecutionGraphRequest(NonEmptyList.of(
          PipelineStageRequest(NonEmptyList.of(
            ModelVariantRequest(
              modelVersionId = 1,
              weight = 100
            )
          ))
        ))
        appDeployer.deploy("test", graph, List.empty).flatMap { res =>
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
            status = Application.Assembling,
            signature = signature.copy(signatureName = "test"),
            kafkaStreaming = List.empty,
            versionGraph = NonEmptyList.of(PipelineStage(
              NonEmptyList.of(Variant(modelVersion, 100)),
              modelVersion.modelContract.predict.get
            ))
          )
        ))
        val versionRepo = mock[ModelVersionRepository[IO]]
        when(versionRepo.get(1)).thenReturn(IO(Some(modelVersion)))
        val servableService = new ServableService[IO] {
          def all(): IO[List[Servable.GenericServable]] = ???
          def getFiltered(name: Option[String], versionId: Option[Long], metadata: Map[String,String]): IO[List[Servable.GenericServable]] = ???
          def deploy(mv: ModelVersion, metadata: Map[String, String]) = {
            val s = Servable(
              modelVersion = mv,
              nameSuffix = "test",
              status = Servable.Serving("Ok", "host", 9090),
              usedApps = Nil
            )
            DeferredResult.completed(s)
          }
          def findAndDeploy(name: String, version: Long, metadata: Map[String, String]) = ???
          def stop(name: String): IO[GenericServable] = ???
          def findAndDeploy(modelId: Long, metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
          def get(name: String): IO[GenericServable] = ???
        }

        val appChanged = ListBuffer.empty[GenericApplication]
        val discoveryHub = new ApplicationEvents.Publisher[IO] {
          override def publish(t: DiscoveryEvent[GenericApplication, String]): IO[Unit] = {
            t match {
              case DiscoveryEvent.Initial => IO.unit
              case DiscoveryEvent.ItemUpdate(items) => IO(appChanged ++= items)
              case DiscoveryEvent.ItemRemove(items) => IO.unit
            }
          }
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
        val appDeployer = ApplicationDeployer.default[IO]()(
          Concurrent[IO],
          applicationRepository = appRepo,
          versionRepository = versionRepo,
          servableService= servableService,
          discoveryHub = discoveryHub,
          graphComposer = graphComposer
        )
        appDeployer.deploy("test", graph, List.empty).flatMap { res =>
          res.completed.get.map { finished =>
            assert(finished.name === "test")
            assert(finished.status.isInstanceOf[Application.Ready])
            assert(appChanged.toList.nonEmpty)
          }
        }
      }
    }
  }

  describe("Application management service") {

    it("should rebuild on update") {
      ioAssert {
        val ogApp = Application(
          id = 1,
          name = "test",
          namespace = None,
          signature = signature.copy(signatureName = "test"),
          status = Application.Assembling,
          kafkaStreaming = List.empty,
          versionGraph =  NonEmptyList.of(PipelineStage(
            NonEmptyList.of(Variant(modelVersion, 100)),
            modelVersion.modelContract.predict.get
          ))
        )
        var app = Option(ogApp)
        val appRepo = mock[ApplicationRepository[IO]]
        when(appRepo.get(Matchers.anyLong())).thenReturn(IO(app))
        when(appRepo.get(Matchers.anyString())).thenReturn(IO(app))
        when(appRepo.create(Matchers.any())).thenReturn(IO(ogApp))
        when(appRepo.update(Matchers.any())).thenReturn(IO(1))
        when(appRepo.delete(Matchers.any())).thenReturn(IO{
          app = None
          1
        })

        val versionRepo = mock[ModelVersionRepository[IO]]
        when(versionRepo.get(Matchers.any[Long]())).thenReturn(IO(Some(modelVersion)))

        val servableService = new ServableService[IO] {
          def all(): IO[List[Servable.GenericServable]] = ???
          def getFiltered(name: Option[String], versionId: Option[Long], metadata: Map[String,String]): IO[List[Servable.GenericServable]] = ???
          def deploy(mv: ModelVersion, metadata: Map[String, String]) = {
            DeferredResult.completed(Servable(mv, "test", Servable.Serving("Ok", "host", 9090), Nil, Map.empty))
          }
          def findAndDeploy(name: String, version: Long, metadata: Map[String, String]) = ???
          def stop(name: String): IO[GenericServable] = ???
          def findAndDeploy(modelId: Long, metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
          def get(name: String): IO[GenericServable] = ???
        }

        val apps = ListBuffer.empty[GenericApplication]
        val eventPublisher = new ApplicationEvents.Publisher[IO] {
          override def publish(t: DiscoveryEvent[GenericApplication, String]): IO[Unit] = {
            t match {
              case DiscoveryEvent.Initial => IO.unit
              case DiscoveryEvent.ItemUpdate(items) => IO(apps ++= items)
              case DiscoveryEvent.ItemRemove(items) => IO(apps.filterNot(a => items.contains(a)))
            }
          }
        }
        val graph = ExecutionGraphRequest(NonEmptyList.of(
          PipelineStageRequest(NonEmptyList.of(
            ModelVariantRequest(
              modelVersionId = 1,
              weight = 100
            )
          ))
        ))
        val appDep = new ApplicationDeployer[IO] {
          override def deploy(name: String, executionGraph: ExecutionGraphRequest, kafkaStreaming: List[ApplicationKafkaStream]): IO[DeferredResult[IO, GenericApplication]] = {
            DeferredResult.completed[IO, GenericApplication](ogApp)
          }
        }
        val applicationService = ApplicationService[IO]()(
          Concurrent[IO],
          appRepo,
          versionRepo,
          servableService,
          eventPublisher,
          appDep
        )
        val updateReq = UpdateApplicationRequest(1, "test", None, graph, Option.empty, Option.empty)
        applicationService.update(updateReq).map { res =>
          assert(res.started.name === "test")
          assert(res.started.status.isInstanceOf[Application.Assembling.type])
        }
      }
    }
  }
}