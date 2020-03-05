package io.hydrosphere.serving.manager.domain

import java.nio.file.{Path, Paths}
import java.time.Instant

import cats.MonadError
import cats.data.NonEmptyList
import cats.effect.{Clock, IO}
import cats.effect.concurrent.Deferred
import cats.syntax.option._
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.contract.model_field.ModelField
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.api.http.controller.model.ModelUploadMetadata
import io.hydrosphere.serving.manager.data_profile_types.DataProfileType
import io.hydrosphere.serving.manager.domain.application.Application.GenericApplication
import io.hydrosphere.serving.manager.domain.application.graph.Variant
import io.hydrosphere.serving.manager.domain.application.graph.VersionGraphComposer.PipelineStage
import io.hydrosphere.serving.manager.domain.application.{Application, ApplicationRepository}
import io.hydrosphere.serving.manager.domain.host_selector.HostSelectorRepository
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model._
import io.hydrosphere.serving.manager.domain.model_build.ModelVersionBuilder
import io.hydrosphere.serving.manager.domain.model_version._
import io.hydrosphere.serving.manager.domain.servable.Servable.GenericServable
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableRepository}
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBApplicationRepository
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.{FetcherResult, ModelFetcher}
import io.hydrosphere.serving.manager.infrastructure.storage.{ModelFileStructure, ModelUnpacker}
import io.hydrosphere.serving.manager.util.DeferredResult
import io.hydrosphere.serving.tensorflow.TensorShape
import io.hydrosphere.serving.tensorflow.types.DataType
import org.mockito.Matchers

class ModelServiceSpec extends GenericUnitTest {
  val dummyImage = DockerImage("a", "b")
  implicit val clock = Clock.create[IO]

  describe("Model service") {
    describe("name validation") {
      it("should reject uppercase letters") {
        assert(ModelValidator.name("ClassifierModel").isEmpty)
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
        val contract = ModelContract("",
          Some(ModelSignature(
            "testSig",
            Seq(ModelField("in", TensorShape.scalar.toProto, DataProfileType.NONE, ModelField.TypeOrSubfields.Dtype(DataType.DT_DOUBLE))),
            Seq(ModelField("out", TensorShape.scalar.toProto, DataProfileType.NONE, ModelField.TypeOrSubfields.Dtype(DataType.DT_DOUBLE)))
          ))
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
          modelContract = contract,
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
          contract = Some(contract),
          profileTypes = None,
          installCommand = None
        )

        val modelRepo = mock[ModelRepository[IO]]
        when(modelRepo.get(Matchers.anyLong())).thenReturn(IO(None))

        val storageMock = mock[ModelUnpacker[IO]]
        when(storageMock.unpack(uploadFile)).thenReturn(IO(ModelFileStructure.forRoot(uploadFile)))
        when(modelRepo.get(modelName)).thenReturn(IO(None))
        when(modelRepo.create(Model(0, modelName))).thenReturn(IO(model))

        val versionBuilder = mock[ModelVersionBuilder[IO]]
        when(versionBuilder.build(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(IO(
          DeferredResult(modelVersion, new Deferred[IO, ModelVersion.Internal] {
            override def get = IO(modelVersion)

            override def complete(a: ModelVersion.Internal) = IO.unit
          })
        ))

        val modelVersionService = mock[ModelVersionService[IO]]
        when(modelVersionService.getNextModelVersion(1)).thenReturn(IO(1L))
        val modelVersionRepository = mock[ModelVersionRepository[IO]]
        val selectorRepo = mock[HostSelectorRepository[IO]]

        val fetcher = new ModelFetcher[IO] {
          override def fetch(path: Path) = IO(None)
        }

        val modelManagementService = ModelService[IO]()(
          MonadError[IO, Throwable],
          clock,
          modelRepository = modelRepo,
          modelVersionService = modelVersionService,
          storageService = storageMock,
          appRepo = null,
          hostSelectorRepository = selectorRepo,
          fetcher = fetcher,
          modelVersionBuilder = versionBuilder,
          servableRepo = null,
          modelVersionRepository = null
        )

        val maybeModel = modelManagementService.uploadModel(uploadFile, upload).attempt.unsafeRunSync()
        assert(maybeModel.isRight, maybeModel)
        val rModel = maybeModel.right.get.started
        println(rModel)
        assert(rModel.model.name === "tf-model")
      }

      it("existing model") {
        val uploadFile = Paths.get("123123")
        val modelName = "upload-model"
        val modelRuntime = DockerImage(
          name = "runtime",
          tag = "latest"
        )
        val model = Model(
          id = 1,
          name = modelName,
        )
        val contract = ModelContract("",
          Some(ModelSignature(
            "testSig",
            Seq(ModelField("in", TensorShape.scalar.toProto, DataProfileType.NONE, ModelField.TypeOrSubfields.Dtype(DataType.DT_DOUBLE))),
            Seq(ModelField("out", TensorShape.scalar.toProto, DataProfileType.NONE, ModelField.TypeOrSubfields.Dtype(DataType.DT_DOUBLE)))
          ))
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
          modelContract = contract,
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
          contract = Some(contract),
          profileTypes = None
        )
        println(upload)

        val modelRepo = mock[ModelRepository[IO]]
        when(modelRepo.update(Matchers.any(classOf[Model]))).thenReturn(IO(1))
        when(modelRepo.get(modelName)).thenReturn(IO(model.some))
        when(modelRepo.get(1)).thenReturn(IO(model.some))

        val storageMock = mock[ModelUnpacker[IO]]
        when(storageMock.unpack(uploadFile)).thenReturn(IO(ModelFileStructure.forRoot(Paths.get(".AAAAAAAAA"))))

        val versionService = mock[ModelVersionBuilder[IO]]
        when(versionService.build(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(IO(
          DeferredResult(modelVersion, new Deferred[IO, ModelVersion.Internal] {
            override def get = IO(modelVersion)

            override def complete(a: ModelVersion.Internal) = IO.unit
          })
        ))
        val fetcher = new ModelFetcher[IO] {
          override def fetch(path: Path) = IO(None)
        }

        val modelManagementService = ModelService[IO]()(
          MonadError[IO, Throwable],
          clock,
          modelRepository = modelRepo,
          modelVersionService = null,
          storageService = storageMock,
          appRepo = null,
          hostSelectorRepository = null,
          fetcher = fetcher,
          modelVersionBuilder = versionService,
          servableRepo = null,
          modelVersionRepository = null
        )

        val maybeModel = modelManagementService.uploadModel(uploadFile, upload).attempt.unsafeRunSync()
        assert(maybeModel.isRight, maybeModel)
        val rModel = maybeModel.right.get.started
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
          contract = None,
          profileTypes = Some(Map("a" -> DataProfileType.IMAGE)),
          installCommand = Some("echo hello"),
          metadata = Some(Map("author" -> "me"))
        )
        val res = ModelVersionMetadata.combineMetadata(fetched, uploaded, None)
        assert(res.modelName === "upload-name")
        assert(res.runtime === DockerImage("test", "test"))
        assert(res.contract === ModelContract.defaultInstance)
        assert(res.hostSelector === None)
        assert(res.installCommand === Some("echo hello"))
        assert(res.metadata === Map("author" -> "me"))
      }

      it("uploaded and fetched") {
        val contract = ModelContract(predict = Some(ModelSignature(
          "sig", Seq.empty, Seq.empty
        )))
        val fetched = Some(FetcherResult(
          modelName = "uuuu",
          modelContract = contract,
          metadata = Map("f" -> "123", "overriden" -> "false")
        ))
        val uploaded = ModelUploadMetadata(
          name = "upload-name",
          runtime = DockerImage("test", "test"),
          hostSelectorName = None,
          contract = None,
          profileTypes = Some(Map("a" -> DataProfileType.IMAGE)),
          installCommand = Some("echo hello"),
          metadata = Some(Map("author" -> "me", "overriden" -> "true"))
        )
        val res = ModelVersionMetadata.combineMetadata(fetched, uploaded, None)
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
          modelContract = ModelContract.defaultInstance,
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
          status = Application.Failed(None),
          signature = ModelSignature.defaultInstance,
          kafkaStreaming = Nil,
          versionGraph = NonEmptyList.of(PipelineStage(NonEmptyList.of(Variant(appFailedVersion, 100)), ModelSignature.defaultInstance))
        )

        val servableFailedModel = Model(2, "servable-failing")
        val servableFailedVersion = ModelVersion.Internal(
          id = 2,
          image = dummyImage,
          created = Instant.now(),
          finished = Some(Instant.now()),
          modelVersion = 2,
          modelContract = ModelContract.defaultInstance,
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
          status = Servable.NotServing("asd", None, None),
          usedApps = Nil,
          metadata = Map.empty
        )
        val okModel = Model(3, "ok")
        val okVersion1 = ModelVersion.Internal(
          id = 3,
          image = dummyImage,
          created = Instant.now(),
          finished = Some(Instant.now()),
          modelVersion = 1,
          modelContract = ModelContract.defaultInstance,
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
          modelContract = ModelContract.defaultInstance,
          runtime = dummyImage,
          model = okModel,
          hostSelector = None,
          status = ModelVersionStatus.Released,
          installCommand = None,
          metadata = Map.empty
        )
        val modelRepo = new ModelRepository[IO] {
          override def create(entity: Model): IO[Model] = ???
          override def get(id: Long): IO[Option[Model]] = {
            id match {
              case appFailedModel.id => IO.pure(Some(appFailedModel))
              case servableFailedModel.id => IO.pure(Some(servableFailedModel))
              case okModel.id => IO.pure(Some(okModel))
              case _ => IO(None)
            }
          }
          override def all(): IO[Seq[Model]] = ???
          override def get(name: String): IO[Option[Model]] = ???
          override def update(value: Model): IO[Int] = ???
          override def delete(id: Long): IO[Int] = IO.pure(1)
        }
        val appRepo = new ApplicationRepository[IO] {
          override def create(entity: GenericApplication): IO[GenericApplication] = ???
          override def get(id: Long): IO[Option[GenericApplication]] = ???
          override def get(name: String): IO[Option[GenericApplication]] = ???
          override def update(value: GenericApplication): IO[Int] = ???
          override def updateRow(row: DBApplicationRepository.ApplicationRow): IO[Int] = ???
          override def delete(id: Long): IO[Int] = ???
          override def all(): IO[List[GenericApplication]] = ???
          override def findVersionUsage(versionIdx: Long): IO[List[GenericApplication]] = {
            versionIdx match {
              case appFailedModel.id => IO(app :: Nil)
              case _ => IO.pure(Nil)
            }
          }
          override def findServableUsage(servableName: String): IO[List[GenericApplication]] = ???
        }
        val servableRepo = new ServableRepository[IO] {
          override def all(): IO[List[GenericServable]] = ???
          override def upsert(servable: GenericServable): IO[GenericServable] = ???
          override def delete(name: String): IO[Int] = ???
          override def get(name: String): IO[Option[GenericServable]] = ???
          override def get(names: Seq[String]): IO[List[GenericServable]] = ???
          override def findForModelVersion(versionId: Long): IO[List[GenericServable]] = {
            versionId match {
              case servableFailedVersion.id =>
                println("Here")
                IO.pure(servable :: Nil)
              case _ =>
                println(s"Ok ${versionId}")
                IO.pure(Nil)
            }
          }
        }
        val modelVersionService = new ModelVersionService[IO] {
          override def all(): IO[List[ModelVersion.Internal]] = ???
          override def get(id: Long): IO[ModelVersion.Internal] = ???
          override def get(name: String, version: Long): IO[ModelVersion.Internal] = ???
          override def getNextModelVersion(modelId: Long): IO[Long] = ???
          override def list: IO[List[ModelVersionView]] = ???
          override def listForModel(modelId: Long): IO[List[ModelVersion.Internal]] = {
            modelId match {
              case appFailedModel.id => IO.pure(appFailedVersion :: Nil)
              case servableFailedModel.id => IO.pure(servableFailedVersion :: Nil)
              case okModel.id => IO.pure(okVersion1 :: okVersion2 :: Nil)
              case _ => IO.raiseError(new RuntimeException(s"Shouldn't delete model $modelId"))
            }
          }
          override def delete(versionId: Long): IO[Option[ModelVersion.Internal]] = {
            versionId match {
              case okVersion1.id => IO.pure(Some(okVersion1))
              case okVersion2.id => IO.pure(Some(okVersion2))
              case _ => IO.raiseError(new RuntimeException(s"Shouldn't delete version $versionId"))
            }
          }
        }
        val modelService = ModelService.apply[IO]()(
          MonadError[IO, Throwable],
          clock,
          modelRepository = modelRepo,
          appRepo = appRepo,
          servableRepo = servableRepo,
          modelVersionService = modelVersionService,
          storageService = null,
          hostSelectorRepository = null,
          fetcher = null,
          modelVersionBuilder = null,
          modelVersionRepository = null
        )

        val result = modelService.deleteModel(okModel.id).unsafeRunSync()
        assert(result.name == okModel.name)

        val failedApp = modelService.deleteModel(appFailedModel.id).attempt.unsafeRunSync()
        assert(failedApp.left.get.isInstanceOf[DomainError.InvalidRequest], failedApp)

        val failedServable = modelService.deleteModel(servableFailedModel.id).attempt.unsafeRunSync()
        assert(failedServable.left.get.isInstanceOf[DomainError.InvalidRequest], failedServable)
      }
    }
  }
}