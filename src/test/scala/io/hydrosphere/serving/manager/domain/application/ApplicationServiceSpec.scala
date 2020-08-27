package io.hydrosphere.serving.manager.domain.application

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.concurrent.Deferred
import cats.effect.{Concurrent, IO}
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.contract.model_field.ModelField
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.discovery.DiscoveryEvent
import io.hydrosphere.serving.manager.domain.{DomainError, deploy_config}
import io.hydrosphere.serving.manager.domain.application.requests._
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version._
import io.hydrosphere.serving.manager.domain.servable.Servable._
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableGC, ServableService}
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
  val modelVersion = ModelVersion.Internal(
    id = 1,
    image = DockerImage("test", "t"),
    created = Instant.now(),
    finished = None,
    modelVersion = 1,
    modelContract = contract,
    runtime = DockerImage("runtime", "v"),
    model = Model(1, "model"),
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

  def appGraph = ApplicationGraph(
    NonEmptyList.of(ApplicationStage(
      NonEmptyList.of(ApplicationServable(modelVersion, 100)),
      modelVersion.modelContract.predict.get
    ))
  )

  describe("Application Deployer") {

    it("should reject application with external ModelVersion") {

      val appRepo = mock[ApplicationRepository[IO]]
      when(appRepo.get("test")).thenReturn(IO(None))

      val versionRepo = mock[ModelVersionRepository[IO]]
      when(versionRepo.get(1)).thenReturn(IO(Some(externalMv)))
      val servableService = new ServableService[IO] {
        def all(): IO[List[Servable.GenericServable]] = ???
        def getFiltered(name: Option[String], versionId: Option[Long], metadata: Map[String,String]): IO[List[Servable.GenericServable]] = ???
        def stop(name: String): IO[GenericServable] = ???
        def get(name: String): IO[GenericServable] = ???
        def findAndDeploy(name: String, version: Long, deployConfigName: Option[String], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
        def findAndDeploy(modelId: Long, deployConfigName: Option[String], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
        def deploy(modelVersion: ModelVersion.Internal, deployConfig: Option[deploy_config.DeploymentConfiguration], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
      }
      val discoveryHub = new ApplicationEvents.Publisher[IO] {
        override def publish(t: DiscoveryEvent[Application, String]): IO[Unit] = IO.unit
      }
      val appDeployer = ApplicationDeployer.default[IO]()(
        Concurrent[IO],
        applicationRepository = appRepo,
        versionRepository = versionRepo,
        servableService= servableService,
        discoveryHub = discoveryHub,
        deploymentConfigService = null,
        monitoringService = null,
        monitoringRepo = null
      )
      val graph = ExecutionGraphRequest(NonEmptyList.of(
        PipelineStageRequest(NonEmptyList.of(
          ModelVariantRequest(
            modelVersionId = 1,
            weight = 100
          )
        ))
      ))
      val result = appDeployer.deploy("test", graph, List.empty).attempt.unsafeRunSync()
      result.left.value.getClass should be (classOf[DomainError.InvalidRequest])
    }

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
            graph = appGraph
          )
        ))
        val versionRepo = mock[ModelVersionRepository[IO]]
        when(versionRepo.get(1)).thenReturn(IO(Some(modelVersion)))
        val servableService = new ServableService[IO] {
          def all(): IO[List[Servable.GenericServable]] = ???
          def getFiltered(name: Option[String], versionId: Option[Long], metadata: Map[String,String]): IO[List[Servable.GenericServable]] = ???
          def stop(name: String): IO[GenericServable] = ???
          def get(name: String): IO[GenericServable] = ???
          override def findAndDeploy(name: String, version: Long, deployConfigName: Option[String], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
          override def findAndDeploy(modelId: Long, deployConfigName: Option[String], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
          override def deploy(modelVersion: ModelVersion.Internal, deployConfig: Option[deploy_config.DeploymentConfiguration], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = {
            IO.pure {
              val s = Servable(modelVersion, "hi", Servable.Serving("Ok", "host", 9090), Nil)
              val d = Deferred[IO, GenericServable].unsafeRunSync()
              d.complete(s).unsafeRunSync()
              DeferredResult(s, d)
            }
          }
        }
        val discoveryHub = new ApplicationEvents.Publisher[IO] {
          override def publish(t: DiscoveryEvent[Application, String]): IO[Unit] = IO.unit
        }
        val appDeployer = ApplicationDeployer.default[IO]()(
          Concurrent[IO],
          applicationRepository = appRepo,
          versionRepository = versionRepo,
          servableService= servableService,
          discoveryHub = discoveryHub,
          deploymentConfigService = null,
          monitoringService = null,
          monitoringRepo = null
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
            graph = appGraph
          )
        ))
        val versionRepo = mock[ModelVersionRepository[IO]]
        when(versionRepo.get(1)).thenReturn(IO(Some(modelVersion)))
        val servableService = new ServableService[IO] {
          def all(): IO[List[Servable.GenericServable]] = ???
          def getFiltered(name: Option[String], versionId: Option[Long], metadata: Map[String,String]): IO[List[Servable.GenericServable]] = ???
          def stop(name: String): IO[GenericServable] = ???
          def get(name: String): IO[GenericServable] = ???
          override def findAndDeploy(name: String, version: Long, deployConfigName: Option[String], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
          override def findAndDeploy(modelId: Long, deployConfigName: Option[String], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
          override def deploy(modelVersion: ModelVersion.Internal, deployConfig: Option[deploy_config.DeploymentConfiguration], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] =  {
            IO.raiseError(new RuntimeException("Test error"))
          }
        }
        val discoveryHub = new ApplicationEvents.Publisher[IO] {
          override def publish(t: DiscoveryEvent[Application, String]): IO[Unit] = IO.unit
        }
        val appDeployer = ApplicationDeployer.default[IO]()(
          Concurrent[IO],
          applicationRepository = appRepo,
          versionRepository = versionRepo,
          servableService= servableService,
          discoveryHub = discoveryHub,
          deploymentConfigService = null,
          monitoringService = null,
          monitoringRepo = null
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
            val status = x.status.asInstanceOf[Application.Failed.type]
            assert(x.statusMessage.get === "Test error")
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
            graph = appGraph
          )
        ))
        val versionRepo = mock[ModelVersionRepository[IO]]
        when(versionRepo.get(1)).thenReturn(IO(Some(modelVersion)))
        val servableService = new ServableService[IO] {
          def all(): IO[List[Servable.GenericServable]] = ???
          def getFiltered(name: Option[String], versionId: Option[Long], metadata: Map[String, String]): IO[List[Servable.GenericServable]] = ???
          def stop(name: String): IO[GenericServable] = ???
          def get(name: String): IO[GenericServable] = ???
          def findAndDeploy(name: String, version: Long, deployConfigName: Option[String], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
          def findAndDeploy(modelId: Long, deployConfigName: Option[String], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
          def deploy(mv: ModelVersion.Internal, deployConfig: Option[deploy_config.DeploymentConfiguration], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = {
            val s = Servable(
              modelVersion = mv,
              nameSuffix = "test",
              status = Servable.Serving("Ok", "host", 9090),
              usedApps = Nil
            )
            DeferredResult.completed(s)
          }
        }

        val appChanged = ListBuffer.empty[Application]
        val discoveryHub = new ApplicationEvents.Publisher[IO] {
          override def publish(t: DiscoveryEvent[Application, String]): IO[Unit] = {
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
        val appDeployer = ApplicationDeployer.default[IO]()(
          Concurrent[IO],
          applicationRepository = appRepo,
          versionRepository = versionRepo,
          servableService= servableService,
          discoveryHub = discoveryHub,
          deploymentConfigService = null,
          monitoringService = null,
          monitoringRepo = null
        )
        appDeployer.deploy("test", graph, List.empty).flatMap { res =>
          res.completed.get.map { finished =>
            assert(finished.name === "test")
            assert(finished.status.isInstanceOf[Application.Ready.type])
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
          graph =  appGraph
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
          def getFiltered(name: Option[String], versionId: Option[Long], metadata: Map[String, String]): IO[List[Servable.GenericServable]] = ???
          def stop(name: String): IO[GenericServable] = ???
          def get(name: String): IO[GenericServable] = ???
          def findAndDeploy(name: String, version: Long, deployConfigName: Option[String], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
          def findAndDeploy(modelId: Long, deployConfigName: Option[String], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = ???
          def deploy(mv: ModelVersion.Internal, deployConfig: Option[deploy_config.DeploymentConfiguration], metadata: Map[String, String]): IO[DeferredResult[IO, GenericServable]] = {
            DeferredResult.completed(Servable(mv, "test", Servable.Serving("Ok", "host", 9090), Nil, Map.empty))
          }
        }

        val apps = ListBuffer.empty[Application]
        val eventPublisher = new ApplicationEvents.Publisher[IO] {
          override def publish(t: DiscoveryEvent[Application, String]): IO[Unit] = {
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
          override def deploy(name: String, executionGraph: ExecutionGraphRequest, kafkaStreaming: List[ApplicationKafkaStream]): IO[DeferredResult[IO, Application]] = {
            DeferredResult.completed[IO, Application](ogApp)
          }
        }
        val gc = ServableGC.noop[IO]()
        val applicationService = ApplicationService[IO]()(
          Concurrent[IO],
          appRepo,
          versionRepo,
          servableService,
          eventPublisher,
          appDep,
          gc
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