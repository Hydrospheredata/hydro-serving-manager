package io.hydrosphere.serving.manager.domain

import java.nio.file.Paths
import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.option._
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.api.http.controller.model.ModelUploadMetadata
import io.hydrosphere.serving.manager.domain.application._
import io.hydrosphere.serving.manager.domain.host_selector.HostSelectorRepository
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model._
import io.hydrosphere.serving.manager.domain.model_build.ModelVersionBuilder
import io.hydrosphere.serving.manager.domain.model_version._
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableRepository}
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.{FetcherResult, ModelFetcher}
import io.hydrosphere.serving.manager.infrastructure.storage.{ModelFileStructure, ModelUnpacker}
import io.hydrosphere.serving.manager.util.DeferredResult

class ModelServiceSpec extends GenericUnitTest {

  describe("Model service") {
    describe("name validation") {
      it("should reject uppercase letters") {
        assert(Model.validate("ClassifierModel").isEmpty)
      }
    }

    describe("uploads") {
      it("a new model") {
        val model = Model(
          id = 1,
          name = "tf-model"
        )
        val modelName = "tf-model"
        val modelRuntime = DockerImage(
          name = "runtime",
          tag = "latest"
        )

        val modelVersion = ModelVersion.Internal(
          id = 1,
          image = DockerImage(
            name = modelName,
            tag = "1"
          ),
          created = Instant.now(),
          finished = Some(Instant.now()),
          modelVersion = 1,
          modelContract = defaultContract,
          runtime = modelRuntime,
          model = model,
          hostSelector = None,
          status = ModelVersionStatus.Released,
          installCommand = None,
          metadata = Map.empty
        )

        val uploadFile = Paths.get("123123")
        val upload = ModelUploadMetadata(
          name = modelName,
          runtime = modelRuntime,
          hostSelectorName = None,
          contract = Some(defaultContract),
          installCommand = None
        )

        val modelRepo   = mock[ModelRepository[IO]]
        val storageMock = mock[ModelUnpacker[IO]]
        when(storageMock.unpack(uploadFile)).thenReturn(IO(ModelFileStructure.forRoot(uploadFile)))
        when(modelRepo.get(modelName)).thenReturn(IO(None))
        when(modelRepo.create(Model(0, modelName))).thenReturn(IO(model))

        val versionBuilder = mock[ModelVersionBuilder[IO]]
        when(versionBuilder.build(any, any, any)).thenReturn(
          DeferredResult.completed(modelVersion)
        )

        val modelVersionService = mock[ModelVersionService[IO]]
        val selectorRepo        = mock[HostSelectorRepository[IO]]

        val fetcher = mock[ModelFetcher[IO]]
        when(fetcher.fetch(any)).thenReturn(IO(None))

        val modelManagementService = ModelService[IO](
          modelRepository = modelRepo,
          modelVersionService = modelVersionService,
          modelUnpacker = storageMock,
          appRepo = null,
          hostSelectorRepository = selectorRepo,
          fetcher = fetcher,
          modelVersionBuilder = versionBuilder,
          servableRepo = null,
          modelVersionRepository = null,
          logger = loggerF
        )

        val maybeModel =
          modelManagementService.uploadModel(uploadFile, upload).attempt.unsafeRunSync()
        assert(maybeModel.isRight, maybeModel)
        val rModel = maybeModel.getOrElse(fail()).started
        println(rModel)
        assert(rModel.model.name === "tf-model")
      }

      it("existing model") {
        val uploadFile = Paths.get("123123")
        val modelName  = "upload-model"
        val modelRuntime = DockerImage(
          name = "runtime",
          tag = "latest"
        )
        val model = Model(
          id = 1,
          name = modelName
        )
        val modelVersion = ModelVersion.Internal(
          id = 1,
          image = DockerImage(
            name = modelName,
            tag = "1"
          ),
          created = Instant.now(),
          finished = Some(Instant.now()),
          modelVersion = 1,
          modelContract = defaultContract,
          runtime = modelRuntime,
          model = model,
          hostSelector = None,
          status = ModelVersionStatus.Released,
          installCommand = None,
          metadata = Map.empty
        )
        val upload = ModelUploadMetadata(
          name = modelName,
          runtime = modelRuntime,
          hostSelectorName = None,
          contract = Some(defaultContract)
        )
        println(upload)

        val modelRepo = mock[ModelRepository[IO]]
        when(modelRepo.get(modelName)).thenReturn(IO(model.some))

        val storageMock = mock[ModelUnpacker[IO]]
        when(storageMock.unpack(uploadFile))
          .thenReturn(IO(ModelFileStructure.forRoot(Paths.get(".AAAAAAAAA"))))

        val versionService = mock[ModelVersionBuilder[IO]]
        when(versionService.build(any, any, any)).thenReturn(
          DeferredResult.completed[IO, ModelVersion.Internal](modelVersion)
        )
        val fetcher = mock[ModelFetcher[IO]]
        when(fetcher.fetch(any)).thenReturn(IO(None))

        val modelManagementService = ModelService[IO](
          modelRepository = modelRepo,
          modelVersionService = null,
          modelUnpacker = storageMock,
          appRepo = null,
          hostSelectorRepository = null,
          fetcher = fetcher,
          modelVersionBuilder = versionService,
          servableRepo = null,
          modelVersionRepository = null,
          logger = loggerF
        )

        val maybeModel =
          modelManagementService.uploadModel(uploadFile, upload).attempt.unsafeRunSync()
        assert(maybeModel.isRight, maybeModel)
        val rModel = maybeModel.getOrElse(fail()).started
        assert(rModel.model.name === "upload-model", rModel)
      }
    }

    describe("combine metadata") {
      it("uploaded and no fetched") {
        val fetched = None
        val uploaded = ModelUploadMetadata(
          name = "upload-name",
          runtime = DockerImage("test", "test"),
          hostSelectorName = None,
          contract = defaultContract.some,
          installCommand = Some("echo hello"),
          metadata = Some(Map("author" -> "me"))
        )
        val res = ModelVersionMetadata.combineMetadata(fetched, uploaded, None).get
        assert(res.modelName === "upload-name")
        assert(res.runtime === DockerImage("test", "test"))
        assert(res.contract === defaultContract)
        assert(res.hostSelector === None)
        assert(res.installCommand === Some("echo hello"))
        assert(res.metadata === Map("author" -> "me"))
      }

      it("uploaded and fetched") {
        val contract = defaultContract
        val fetched = Some(
          FetcherResult(
            modelName = "uuuu",
            modelContract = contract,
            metadata = Map("f" -> "123", "overriden" -> "false")
          )
        )
        val uploaded = ModelUploadMetadata(
          name = "upload-name",
          runtime = DockerImage("test", "test"),
          hostSelectorName = None,
          contract = None,
          installCommand = Some("echo hello"),
          metadata = Some(Map("author" -> "me", "overriden" -> "true"))
        )
        val res = ModelVersionMetadata.combineMetadata(fetched, uploaded, None).get
        assert(res.modelName === "upload-name")
        assert(res.runtime === DockerImage("test", "test"))
        assert(res.contract === contract)
        assert(res.hostSelector === None)
        assert(res.installCommand === Some("echo hello"))
        assert(res.metadata === Map("author" -> "me", "overriden" -> "true", "f" -> "123"))
      }
    }

    describe("CRUD") {
      it("should correctly delete a model and fail if there are live deps") {
        val appFailedModel = Model(1, "app-failing")
        val appFailedVersion = ModelVersion.Internal(
          id = 1,
          image = dummyImage,
          created = Instant.now(),
          finished = Some(Instant.now()),
          modelVersion = 1,
          modelContract = defaultContract,
          runtime = dummyImage,
          model = appFailedModel,
          hostSelector = None,
          status = ModelVersionStatus.Released,
          installCommand = None,
          metadata = Map.empty
        )
        val app = Application(
          id = 1,
          name = "app",
          namespace = None,
          status = Application.Status.Failed,
          kafkaStreaming = Nil,
          executionGraph = ApplicationGraph(
            NonEmptyList.of(
              WeightedNode(
                NonEmptyList.of(Variant(appFailedVersion, None, 100)),
                defaultContract.predict
              )
            ),
            defaultContract.predict
          ),
          message = "Failed"
        )

        val servableFailedModel = Model(2, "servable-failing")
        val servableFailedVersion = ModelVersion.Internal(
          id = 2,
          image = dummyImage,
          created = Instant.now(),
          finished = Some(Instant.now()),
          modelVersion = 2,
          modelContract = defaultContract,
          runtime = dummyImage,
          model = servableFailedModel,
          hostSelector = None,
          status = ModelVersionStatus.Released,
          installCommand = None,
          metadata = Map.empty
        )
        val servable = Servable(
          modelVersion = servableFailedVersion,
          nameSuffix = "123",
          status = Servable.Status.NotServing,
          usedApps = Nil,
          metadata = Map.empty,
          message = "ok",
          host = None,
          port = None
        )
        val okModel = Model(3, "ok")
        val okVersion1 = ModelVersion.Internal(
          id = 3,
          image = dummyImage,
          created = Instant.now(),
          finished = Some(Instant.now()),
          modelVersion = 1,
          modelContract = defaultContract,
          runtime = dummyImage,
          model = okModel,
          hostSelector = None,
          status = ModelVersionStatus.Released,
          installCommand = None,
          metadata = Map.empty
        )
        val okVersion2 = ModelVersion.Internal(
          id = 4,
          image = dummyImage,
          created = Instant.now(),
          finished = Some(Instant.now()),
          modelVersion = 2,
          modelContract = defaultContract,
          runtime = dummyImage,
          model = okModel,
          hostSelector = None,
          status = ModelVersionStatus.Released,
          installCommand = None,
          metadata = Map.empty
        )
        val modelRepo = mock[ModelRepository[IO]]
        when(modelRepo.get(anyLong)).thenAnswer[Long] {
          case appFailedModel.id      => IO.pure(Some(appFailedModel))
          case servableFailedModel.id => IO.pure(Some(servableFailedModel))
          case okModel.id             => IO.pure(Some(okModel))
          case _                      => IO(None)
        }
        when(modelRepo.delete(anyLong)).thenReturn(IO(1))

        val appRepo = mock[ApplicationRepository[IO]]
        when(appRepo.findVersionUsage(anyLong)).thenAnswer[Long] {
          case appFailedModel.id => IO(app :: Nil)
          case _                 => IO.pure(Nil)
        }

        val servableRepoMock = mock[ServableRepository[IO]]
        when(servableRepoMock.findForModelVersion(anyLong)).thenAnswer[Long] {
          case servableFailedVersion.id =>
            IO.pure(servable :: Nil)
          case x =>
            IO.pure(Nil)
        }

        val versionServiceMock = mock[ModelVersionService[IO]]
        when(versionServiceMock.listForModel(anyLong)).thenAnswer[Long] {
          case appFailedModel.id      => IO.pure(appFailedVersion :: Nil)
          case servableFailedModel.id => IO.pure(servableFailedVersion :: Nil)
          case okModel.id             => IO.pure(okVersion1 :: okVersion2 :: Nil)
          case x                      => IO.raiseError(new RuntimeException(s"Shouldn't delete model $x"))
        }

        when(versionServiceMock.delete(anyLong)).thenAnswer[Long] {
          case okVersion1.id => IO.pure(Some(okVersion1))
          case okVersion2.id => IO.pure(Some(okVersion2))
          case x             => IO.raiseError(new RuntimeException(s"Shouldn't delete version $x"))
        }
        val modelService = ModelService.apply[IO](
          modelRepository = modelRepo,
          appRepo = appRepo,
          servableRepo = servableRepoMock,
          modelVersionService = versionServiceMock,
          modelUnpacker = null,
          hostSelectorRepository = null,
          fetcher = null,
          modelVersionBuilder = null,
          modelVersionRepository = null,
          logger = loggerF
        )

        val result = modelService.deleteModel(okModel.id).unsafeRunSync()
        assert(result.name == okModel.name)

        val failedApp = modelService.deleteModel(appFailedModel.id).attempt.unsafeRunSync()
        assert(
          failedApp.swap
            .getOrElse(fail())
            .isInstanceOf[DomainError.InvalidRequest],
          failedApp
        )

        val failedServable =
          modelService.deleteModel(servableFailedModel.id).attempt.unsafeRunSync()
        assert(
          failedServable.swap
            .getOrElse(fail())
            .isInstanceOf[DomainError.InvalidRequest],
          failedServable
        )
      }
    }
  }
}
