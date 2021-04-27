package io.hydrosphere.serving.manager.domain.application

import cats.data.NonEmptyList
import cats.effect.{Concurrent, IO}
import cats.syntax.applicative._
import cats.syntax.option._
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.application.requests._
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfiguration
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version._
import io.hydrosphere.serving.manager.domain.monitoring._
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableGC, ServableService}
import org.mockito.Mockito

import java.time.Instant
import scala.collection.mutable.ListBuffer

class ApplicationServiceSpec extends GenericUnitTest {
  val signature: Signature = Signature.defaultSignature

  val modelVersion: ModelVersion.Internal = ModelVersion.Internal(
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
  val externalMv: ModelVersion.External = ModelVersion.External(
    id = 1,
    created = Instant.now(),
    modelVersion = 1,
    modelSignature = signature,
    model = Model(1, "model"),
    metadata = Map.empty
  )

  def appGraph: ApplicationGraph =
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

      val servableService = mock[ServableService[IO]]
      val monitoringRepo  = mock[MonitoringRepository[IO]]
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
          port = None,
          deploymentConfiguration = DeploymentConfiguration.empty
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
        when(appRepo.create(any)).thenReturn(
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

        val servableService = mock[ServableService[IO]]
        when(servableService.deploy(any[ModelVersion.Internal], any, anyMap)).thenReturn {
          IO.pure {
            val s = Servable(
              modelVersion,
              "hi",
              Servable.Status.Serving,
              message = "ok".some,
              host = Some("hoat"),
              port = Some(9090),
              deploymentConfiguration = DeploymentConfiguration.empty
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
  }

  describe("Application management service") {

    it("should rebuild on update") {
      ioAssert {
        val ogApp = Application(
          id = 1,
          name = "test",
          namespace = None,
          signature = signature.copy(signatureName = "test"),
          kafkaStreaming = List.empty,
          graph = appGraph
        )
        var app     = Option(ogApp)
        val appRepo = mock[ApplicationRepository[IO]]
        when(appRepo.get(anyLong)).thenReturn(IO(app))
        when(appRepo.get(any[String])).thenReturn(IO(app))
        when(appRepo.create(any)).thenReturn(IO(ogApp))
        when(appRepo.update(any)).thenReturn(IO(1))
        when(appRepo.delete(any)).thenReturn(IO {
          app = None
          1
        })

        val versionRepo = mock[ModelVersionRepository[IO]]
        when(versionRepo.get(anyLong)).thenReturn(IO(Some(modelVersion)))

        val servableService = mock[ServableService[IO]]
        when(servableService.deploy(eqTo(modelVersion), eqTo(None), any))
          .thenAnswer {
            (
                mv: ModelVersion.Internal,
                _: Option[DeploymentConfiguration],
                _: Map[String, String]
            ) =>
              IO {
                Servable(
                  mv,
                  "test",
                  Servable.Status.Serving,
                  message = "Ok".some,
                  host = Some("host"),
                  port = Some(9090),
                  deploymentConfiguration = DeploymentConfiguration.empty
                )
              }
          }

        val apps = ListBuffer.empty[Application]
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
        val appDep = new ApplicationDeployer[IO] {
          override def deploy(
              name: String,
              executionGraph: ExecutionGraphRequest,
              kafkaStreaming: List[ApplicationKafkaStream],
              meta: Map[String, String]
          ): IO[Application] = IO(ogApp)
        }

        val gc = ServableGC.noop[IO]()
        val applicationService = ApplicationService[IO]()(
          Concurrent[IO],
          appRepo,
          versionRepo,
          servableService,
          appDep,
          gc
        )
        val updateReq = UpdateApplicationRequest(1, "test", None, graph, Option.empty, Option.empty)

        applicationService.update(updateReq).map { res =>
          assert(res.name === "test")
          assert(res.status.isInstanceOf[Application.Status.Assembling.type])
        }
      }
    }
  }
}
