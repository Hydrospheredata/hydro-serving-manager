package io.hydrosphere.serving.manager.domain.application

import java.time.Instant
import cats.data.NonEmptyList
import cats.effect.{Concurrent, IO}
import cats.syntax.applicative._
import cats.syntax.option._

import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.{deploy_config, DomainError}
import io.hydrosphere.serving.manager.domain.application.requests._
import io.hydrosphere.serving.manager.domain.contract.DataType.DT_DOUBLE
import io.hydrosphere.serving.manager.domain.contract.{Field, Signature, TensorShape}
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version._
import io.hydrosphere.serving.manager.domain.monitoring.{
  CustomModelMetricSpec,
  CustomModelMetricSpecConfiguration,
  Monitoring,
  MonitoringRepository,
  ThresholdCmpOperator
}
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableGC, ServableService}
import io.hydrosphere.serving.manager.util.DeferredResult
import org.mockito.{Matchers, Mockito}

import scala.collection.mutable.ListBuffer

class ApplicationServiceSpec extends GenericUnitTest {
  val signature = Signature(
    "claim",
    NonEmptyList.of(
      Field.Tensor("in", DT_DOUBLE, TensorShape.Dynamic, None)
    ),
    NonEmptyList.of(
      Field.Tensor("out", DT_DOUBLE, TensorShape.Dynamic, None)
    )
  )

  val modelVersion = ModelVersion.Internal(
    id = 1,
    image = DockerImage("test", "t"),
    created = Instant.now(),
    finished = None,
    modelVersion = 1,
    modelSignature = signature,
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
    modelSignature = signature,
    model = Model(1, "model"),
    metadata = Map.empty
  )

  def appGraph =
    ApplicationGraph(
      NonEmptyList.of(
        ApplicationStage(
          NonEmptyList.of(ApplicationServable(modelVersion, 100)),
          modelVersion.modelSignature
        )
      )
    )

  describe("Application Deployer") {

    it("should reject application with external ModelVersion") {

      val appRepo = mock[ApplicationRepository[IO]]
      when(appRepo.get("test")).thenReturn(IO(None))

      val versionRepo = mock[ModelVersionRepository[IO]]
      when(versionRepo.get(1)).thenReturn(IO(Some(externalMv)))
      val servableService = new ServableService[IO] {
        def all(): IO[List[Servable]] = ???
        def getFiltered(
            name: Option[String],
            versionId: Option[Long],
            metadata: Map[String, String]
        ): IO[List[Servable]]                = ???
        def stop(name: String): IO[Servable] = ???
        def get(name: String): IO[Servable]  = ???
        def findAndDeploy(
            name: String,
            version: Long,
            deployConfigName: Option[String],
            metadata: Map[String, String]
        ): IO[Servable] = ???
        def findAndDeploy(
            modelId: Long,
            deployConfigName: Option[String],
            metadata: Map[String, String]
        ): IO[Servable] = ???
        def deploy(
            modelVersion: ModelVersion.Internal,
            deployConfig: Option[deploy_config.DeploymentConfiguration],
            metadata: Map[String, String]
        ): IO[Servable] = ???
      }
      val monitoringRepo = mock[MonitoringRepository[IO]]
      val appDeployer = ApplicationDeployer.default[IO]()(
        Concurrent[IO],
        applicationRepository = appRepo,
        versionRepository = versionRepo,
        servableService = servableService,
        deploymentConfigService = null,
        monitoringService = null,
        monitoringRepo = monitoringRepo
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
      val result = appDeployer.deploy("test", graph, List.empty, Map.empty).attempt.unsafeRunSync()
      result.left.value.getClass should be(classOf[DomainError.InvalidRequest])
    }

    it("should start application build") {
      ioAssert {
        val appRepo = mock[ApplicationRepository[IO]]
        val startingServable = Servable(
          modelVersion = modelVersion,
          name = "",
          status = Servable.Status.Starting,
          message = None,
          host = None,
          port = None
        )
        val graphWithStartingServable = ApplicationGraph(
          NonEmptyList.of(
            ApplicationStage(
              NonEmptyList.of(ApplicationServable(modelVersion, 100, startingServable.some)),
              modelVersion.modelSignature
            )
          )
        )

        when(appRepo.get("test")).thenReturn(IO(None))
        when(appRepo.create(Matchers.any())).thenReturn(
          IO(
            Application(
              id = 1,
              name = "test",
              namespace = None,
              signature = signature.copy(signatureName = "test"),
              kafkaStreaming = List.empty,
              graphWithStartingServable
            )
          )
        )
        val versionRepo = mock[ModelVersionRepository[IO]]
        when(versionRepo.get(1)).thenReturn(IO(Some(modelVersion)))
        val servableService = new ServableService[IO] {
          def all(): IO[List[Servable]] = ???
          def getFiltered(
              name: Option[String],
              versionId: Option[Long],
              metadata: Map[String, String]
          ): IO[List[Servable]]                = ???
          def stop(name: String): IO[Servable] = ???
          def get(name: String): IO[Servable]  = ???
          override def findAndDeploy(
              name: String,
              version: Long,
              deployConfigName: Option[String],
              metadata: Map[String, String]
          ): IO[Servable] = ???
          override def findAndDeploy(
              modelId: Long,
              deployConfigName: Option[String],
              metadata: Map[String, String]
          ): IO[Servable] = ???
          override def deploy(
              modelVersion: ModelVersion.Internal,
              deployConfig: Option[deploy_config.DeploymentConfiguration],
              metadata: Map[String, String]
          ): IO[Servable] =
            IO.pure {
              val s = Servable(
                modelVersion,
                "hi",
                Servable.Status.Serving,
                message = "ok".some,
                host = Some("hoat"),
                port = Some(9090)
              )
              s
            }
        }

        val monitoringRepo = mock[MonitoringRepository[IO]]

        val appDeployer = ApplicationDeployer.default[IO]()(
          Concurrent[IO],
          applicationRepository = appRepo,
          versionRepository = versionRepo,
          servableService = servableService,
          deploymentConfigService = null,
          monitoringService = null,
          monitoringRepo = monitoringRepo
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
        appDeployer.deploy("test", graph, List.empty, Map.empty).map { res =>
          assert(res.name == "test")
          assert(res.status.isInstanceOf[Application.Status.Assembling.type])
        // build will fail nonetheless
        }
      }
    }

//    it("should handle failed application builds") {
//      ioAssert {
//        val appRepo = mock[ApplicationRepository[IO]]
//        when(appRepo.update(Matchers.any())).thenReturn(IO.pure(1))
//        when(appRepo.get("test")).thenReturn(IO(None))
//        when(appRepo.create(Matchers.any())).thenReturn(
//          IO(
//            Application(
//              id = 1,
//              name = "test",
//              namespace = None,
//              status = Application.Status.Assembling,
//              signature = signature.copy(signatureName = "test"),
//              kafkaStreaming = List.empty,
//              graph = appGraph
//            )
//          )
//        )
//        val versionRepo = mock[ModelVersionRepository[IO]]
//        when(versionRepo.get(1)).thenReturn(IO(Some(modelVersion)))
//        val servableService = new ServableService[IO] {
//          def all(): IO[List[Servable]] = ???
//          def getFiltered(
//              name: Option[String],
//              versionId: Option[Long],
//              metadata: Map[String, String]
//          ): IO[List[Servable]]                = ???
//          def stop(name: String): IO[Servable] = ???
//          def get(name: String): IO[Servable]  = ???
//          override def findAndDeploy(
//              name: String,
//              version: Long,
//              deployConfigName: Option[String],
//              metadata: Map[String, String]
//          ): IO[Servable] = ???
//          override def findAndDeploy(
//              modelId: Long,
//              deployConfigName: Option[String],
//              metadata: Map[String, String]
//          ): IO[Servable] = ???
//          override def deploy(
//              modelVersion: ModelVersion.Internal,
//              deployConfig: Option[deploy_config.DeploymentConfiguration],
//              metadata: Map[String, String]
//          ): IO[Servable] =
//              IO {
//                Servable(
//                  modelVersion,
//                  "kek",
//                  Servable.Status.NotServing,
//                  message = "error".some,
//                  host = None,
//                  port = None
//                )
//              }
//        }
//        val monitoringRepo = mock[MonitoringRepository[IO]]
//        when(monitoringRepo.forModelVersion(1)).thenReturn(Nil.pure[IO])
//        val appDeployer = ApplicationDeployer.default[IO]()(
//          Concurrent[IO],
//          applicationRepository = appRepo,
//          versionRepository = versionRepo,
//          servableService = servableService,
//          deploymentConfigService = null,
//          monitoringService = null,
//          monitoringRepo = monitoringRepo
//        )
//        val graph = ExecutionGraphRequest(
//          NonEmptyList.of(
//            PipelineStageRequest(
//              NonEmptyList.of(
//                ModelVariantRequest(
//                  modelVersionId = 1,
//                  weight = 100
//                )
//              )
//            )
//          )
//        )
//
//        appDeployer.deploy("test", graph, List.empty, Map.empty).map { res =>
//          println("Waiting for build")
//            assert(res.status == Application.Status.Failed)
//            assert(res.statusMessage.get == "Servable model-1-kek is in invalid state: error")
//        }
//      }
//    }

//    it("should handle finished builds") {
//      ioAssert {
//        val appRepo = mock[ApplicationRepository[IO]]
//        when(appRepo.update(Matchers.any())).thenReturn(IO(1))
//        when(appRepo.get("test")).thenReturn(IO(None))
//        when(appRepo.create(Matchers.any())).thenReturn(
//          IO(
//            Application(
//              id = 1,
//              name = "test",
//              namespace = None,
//              status = Application.Status.Assembling,
//              signature = signature.copy(signatureName = "test"),
//              kafkaStreaming = List.empty,
//              graph = appGraph
//            )
//          )
//        )
//        val versionRepo = mock[ModelVersionRepository[IO]]
//        when(versionRepo.get(1)).thenReturn(IO(Some(modelVersion)))
//        val servableService = new ServableService[IO] {
//          def all(): IO[List[Servable]] = ???
//          def getFiltered(
//              name: Option[String],
//              versionId: Option[Long],
//              metadata: Map[String, String]
//          ): IO[List[Servable]]                = ???
//          def stop(name: String): IO[Servable] = ???
//          def get(name: String): IO[Servable]  = ???
//          def findAndDeploy(
//              name: String,
//              version: Long,
//              deployConfigName: Option[String],
//              metadata: Map[String, String]
//          ): IO[Servable] = ???
//          def findAndDeploy(
//              modelId: Long,
//              deployConfigName: Option[String],
//              metadata: Map[String, String]
//          ): IO[Servable] = ???
//          def deploy(
//              mv: ModelVersion.Internal,
//              deployConfig: Option[deploy_config.DeploymentConfiguration],
//              metadata: Map[String, String]
//          ): IO[Servable] = {
//            IO {
//              Servable(
//                modelVersion = mv,
//                name = "test",
//                status = Servable.Status.Serving,
//                usedApps = Nil,
//                message = "Ok".some,
//                host = Some("host"),
//                port = Some(9090)
//              )
//            }
//          }
//        }
//
//        val graph = ExecutionGraphRequest(
//          NonEmptyList.of(
//            PipelineStageRequest(
//              NonEmptyList.of(
//                ModelVariantRequest(
//                  modelVersionId = 1,
//                  weight = 100
//                )
//              )
//            )
//          )
//        )
//        val monitoringRepo = mock[MonitoringRepository[IO]]
//        when(monitoringRepo.forModelVersion(1)).thenReturn(Nil.pure[IO])
//        val appDeployer = ApplicationDeployer.default[IO]()(
//          Concurrent[IO],
//          applicationRepository = appRepo,
//          versionRepository = versionRepo,
//          servableService = servableService,
//          deploymentConfigService = null,
//          monitoringService = null,
//          monitoringRepo = monitoringRepo
//        )
//        appDeployer.deploy("test", graph, List.empty, Map.empty).map { res =>
//            assert(res.name == "test")
//            assert(res.status.isInstanceOf[Application.Status.Ready.type])
//        }
//      }
//    }

//    it("should recreate missing MetricSpec Servables") {
//      ioAssert {
//        val appRepo = mock[ApplicationRepository[IO]]
//        when(appRepo.update(Matchers.any())).thenReturn(IO(1))
//        when(appRepo.get("test")).thenReturn(IO(None))
//        when(appRepo.create(Matchers.any())).thenReturn(
//          IO(
//            Application(
//              id = 1,
//              name = "test",
//              namespace = None,
//              status = Application.Status.Assembling,
//              signature = signature.copy(signatureName = "test"),
//              kafkaStreaming = List.empty,
//              graph = appGraph
//            )
//          )
//        )
//        val versionRepo = mock[ModelVersionRepository[IO]]
//        when(versionRepo.get(1)).thenReturn(IO(Some(modelVersion)))
//        val servableService = mock[ServableService[IO]]
//        when(servableService.deploy(Matchers.eq(modelVersion), Matchers.eq(None), Matchers.any()))
//          .thenReturn {
//             IO {
//               Servable(
//                 modelVersion = modelVersion,
//                 name = "test",
//                 status = Servable.Status.Serving,
//                 usedApps = Nil,
//                 message = "Ok"some,
//                 host = Some("host"),
//                 port = Some(9090)
//               )
//             }
//          }
//
//        val spec = CustomModelMetricSpec(
//          name = "test1",
//          modelVersionId = modelVersion.id,
//          config = CustomModelMetricSpecConfiguration(
//            modelVersionId = 2,
//            threshold = 2,
//            thresholdCmpOperator = ThresholdCmpOperator.Eq,
//            servable = None,
//            deploymentConfigName = None
//          )
//        )
//        val monitoringRepo = mock[MonitoringRepository[IO]]
//        when(monitoringRepo.forModelVersion(1)).thenReturn(List(spec).pure[IO])
//
//        val monitoringService = mock[Monitoring[IO]]
//        when(monitoringService.deployServable(spec)).thenReturn(spec.pure[IO])
//        val appDeployer = ApplicationDeployer.default[IO]()(
//          Concurrent[IO],
//          applicationRepository = appRepo,
//          versionRepository = versionRepo,
//          servableService = servableService,
//          deploymentConfigService = null,
//          monitoringService = monitoringService,
//          monitoringRepo = monitoringRepo
//        )
//        val graph = ExecutionGraphRequest(
//          NonEmptyList.of(
//            PipelineStageRequest(
//              NonEmptyList.of(
//                ModelVariantRequest(
//                  modelVersionId = 1,
//                  weight = 100
//                )
//              )
//            )
//          )
//        )
//        val app = appDeployer.deploy("test", graph, List.empty, Map.empty)
//
//        app.map { res =>
//            Mockito.verify(monitoringService).deployServable(spec)
//            assert(res.name == "test")
//            assert(res.status.isInstanceOf[Application.Status.Ready.type])
//        }
//      }
//    }
  }

//  describe("Application management service") {
//
//    it("should rebuild on update") {
//      ioAssert {
//        val ogApp = Application(
//          id = 1,
//          name = "test",
//          namespace = None,
//          signature = signature.copy(signatureName = "test"),
//          status = Application.Status.Assembling,
//          kafkaStreaming = List.empty,
//          graph = appGraph
//        )
//        var app     = Option(ogApp)
//        val appRepo = mock[ApplicationRepository[IO]]
//        when(appRepo.get(Matchers.anyLong())).thenReturn(IO(app))
//        when(appRepo.get(Matchers.anyString())).thenReturn(IO(app))
//        when(appRepo.create(Matchers.any())).thenReturn(IO(ogApp))
//        when(appRepo.update(Matchers.any())).thenReturn(IO(1))
//        when(appRepo.delete(Matchers.any())).thenReturn(IO {
//          app = None
//          1
//        })
//
//        val versionRepo = mock[ModelVersionRepository[IO]]
//        when(versionRepo.get(Matchers.any[Long]())).thenReturn(IO(Some(modelVersion)))
//
//        val servableService = new ServableService[IO] {
//          def all(): IO[List[Servable]] = ???
//          def getFiltered(
//              name: Option[String],
//              versionId: Option[Long],
//              metadata: Map[String, String]
//          ): IO[List[Servable]]                = ???
//          def stop(name: String): IO[Servable] = ???
//          def get(name: String): IO[Servable]  = ???
//          def findAndDeploy(
//              name: String,
//              version: Long,
//              deployConfigName: Option[String],
//              metadata: Map[String, String]
//          ): IO[Servable] = ???
//          def findAndDeploy(
//              modelId: Long,
//              deployConfigName: Option[String],
//              metadata: Map[String, String]
//          ): IO[Servable] = ???
//          def deploy(
//              mv: ModelVersion.Internal,
//              deployConfig: Option[deploy_config.DeploymentConfiguration],
//              metadata: Map[String, String]
//          ): IO[Servable] =
//            IO {
//              Servable(
//                mv,
//                "test",
//                Servable.Status.Serving,
//                message = "Ok".some,
//                host = Some("host"),
//                port = Some(9090)
//              )
//            }
//        }
//
//        val apps = ListBuffer.empty[Application]
//        val graph = ExecutionGraphRequest(
//          NonEmptyList.of(
//            PipelineStageRequest(
//              NonEmptyList.of(
//                ModelVariantRequest(
//                  modelVersionId = 1,
//                  weight = 100
//                )
//              )
//            )
//          )
//        )
//        val appDep = new ApplicationDeployer[IO] {
//          override def deploy(
//              name: String,
//              executionGraph: ExecutionGraphRequest,
//              kafkaStreaming: List[ApplicationKafkaStream],
//              meta: Map[String, String]
//          ): IO[Application] = IO { ogApp }
//        }
//        val gc = ServableGC.noop[IO]()
//        val applicationService = ApplicationService[IO]()(
//          Concurrent[IO],
//          appRepo,
//          versionRepo,
//          servableService,
//          appDep,
//          gc
//        )
//        val updateReq = UpdateApplicationRequest(1, "test", None, graph, Option.empty, Option.empty)
//        applicationService.update(updateReq).map { res =>
//          assert(res.name == "test")
//          assert(res.status.isInstanceOf[Application.Status.Assembling.type])
//        }
//      }
//    }
//  }
}
