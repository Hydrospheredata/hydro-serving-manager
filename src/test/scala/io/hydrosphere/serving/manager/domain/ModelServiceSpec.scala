package io.hydrosphere.serving.manager.domain

import java.nio.file.{Path, Paths}
import java.time.Instant
import cats.MonadError
import cats.data.NonEmptyList
import cats.effect.{Clock, IO}
import cats.effect.concurrent.Deferred
import cats.syntax.option._
import io.hydrosphere.serving.manager.domain.contract.DataProfileType.IMAGE
import io.hydrosphere.serving.manager.domain.contract.DataType.{DT_FLOAT, DT_INT32}
import io.hydrosphere.serving.manager.domain.contract.Signature.{defaultSignature, validate}
import io.hydrosphere.serving.manager.domain.contract.TensorShape
import io.hydrosphere.serving.manager.domain.deploy_config.{
  DeploymentConfiguration,
  DeploymentConfigurationRepository
}
import io.hydrosphere.serving.proto.contract.signature.ModelSignature
//import io.hydrosphere.serving.contract.model_contract.ModelContract
//import io.hydrosphere.serving.contract.model_field.ModelField
//import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.api.http.controller.model.ModelUploadMetadata
//import io.hydrosphere.serving.manager.data_profile_types.DataProfileType
import io.hydrosphere.serving.manager.domain.application.{
  Application,
  ApplicationGraph,
  ApplicationRepository,
  ApplicationServable,
  ApplicationStage
}
import io.hydrosphere.serving.manager.domain.contract.{DataProfileType, DataType, Field, Signature}
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model._
import io.hydrosphere.serving.manager.domain.model_build.ModelVersionBuilder
import io.hydrosphere.serving.manager.domain.model_version._
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableRepository}
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBApplicationRepository
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.{FetcherResult, ModelFetcher}
import io.hydrosphere.serving.manager.infrastructure.storage.{ModelFileStructure, ModelUnpacker}
import io.hydrosphere.serving.manager.util.DeferredResult
import io.hydrosphere.serving.proto.contract.field.ModelField
import io.hydrosphere.serving.proto.contract.types.{DataProfileType, DataType}
import org.mockito.Matchers

class ModelServiceSpec extends GenericUnitTest {
  val dummyImage     = DockerImage("a", "b")
  implicit val clock = Clock.create[IO]

  describe("Model service") {
    describe("name validation") {
      it("should reject uppercase letters") {
        assert(ModelValidator.name("ClassifierModel").isEmpty)
      }
    }

    describe("contract validation") {
      it("should fail if name is empty") {
        val input     = Field.Tensor("input", DT_INT32, TensorShape.Dynamic, None)
        val output    = Field.Tensor("output", DT_INT32, TensorShape.Dynamic, None)
        val signature = Signature.apply("", NonEmptyList.one(input), NonEmptyList.one(output))
        assert(validate(signature).isInvalid, true)
      }

//      it("should fail if input contains invalid dtype") {
//        val inputs = Seq(
//          ModelField(
//            "name1",
//            None,
//            ModelField.TypeOrSubfields.Dtype(DataType.DT_INVALID),
//            DataProfileType.NONE
//          ),
//          ModelField(
//            "name2",
//            None,
//            ModelField.TypeOrSubfields.Dtype(DataType.DT_STRING),
//            DataProfileType.NONE
//          )
//        )
//        val outputs = Seq(
//          ModelField(
//            "name3",
//            None,
//            ModelField.TypeOrSubfields.Dtype(DataType.DT_STRING),
//            DataProfileType.NONE
//          )
//        )
//        val contract = ModelContract(predict = Some(ModelSignature("sig", inputs, outputs)))
//        val res      = Contract.validateContract(contract)
//        assert(res.isInvalid, res)
//      }
//      it("should fail if output contains invalid dtype") {
//        val inputs = Seq(
//          ModelField(
//            "name1",
//            None,
//            ModelField.TypeOrSubfields.Dtype(DataType.DT_STRING),
//            DataProfileType.NONE
//          )
//        )
//        val outputs = Seq(
//          ModelField(
//            "name2",
//            None,
//            ModelField.TypeOrSubfields.Dtype(DataType.DT_INVALID),
//            DataProfileType.NONE
//          ),
//          ModelField(
//            "name2",
//            None,
//            ModelField.TypeOrSubfields.Dtype(DataType.DT_STRING),
//            DataProfileType.NONE
//          )
//        )
//        val contract = ModelContract(predict = Some(ModelSignature("sig", inputs, outputs)))
//        val res      = Contract.validateContract(contract)
//        assert(res.isInvalid, res)
//      }
//      it("should pass if ok") {
//        val inputs = Seq(
//          ModelField(
//            "name1",
//            None,
//            ModelField.TypeOrSubfields.Dtype(DataType.DT_STRING),
//            DataProfileType.NONE
//          )
//        )
//        val outputs = Seq(
//          ModelField(
//            "name2",
//            None,
//            ModelField.TypeOrSubfields.Dtype(DataType.DT_BOOL),
//            DataProfileType.NONE
//          ),
//          ModelField(
//            "name2",
//            None,
//            ModelField.TypeOrSubfields.Dtype(DataType.DT_STRING),
//            DataProfileType.NONE
//          )
//        )
//        val contract = ModelContract(predict = Some(ModelSignature("sig", inputs, outputs)))
//        val res      = Contract.validateContract(contract)
//        assert(res.isValid, res)
//      }
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
        val signature = Signature(
          "testSig",
          NonEmptyList.of(Field.Tensor("input", DT_INT32, TensorShape.Dynamic, None)),
          NonEmptyList.of(Field.Tensor("output", DT_INT32, TensorShape.Dynamic, None))
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
          modelSignature = signature,
          runtime = modelRuntime,
          model = model,
          status = ModelVersionStatus.Released,
          installCommand = None,
          metadata = Map.empty
        )

        val uploadFile = Paths.get("123123")
        val upload = ModelUploadMetadata(
          name = modelName,
          runtime = modelRuntime,
          hostSelectorName = None,
          modelSignature = Some(signature),
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
        when(versionBuilder.build(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(
          IO(
            DeferredResult(
              modelVersion,
              new Deferred[IO, ModelVersion.Internal] {
                override def get = IO(modelVersion)

                override def complete(a: ModelVersion.Internal) = IO.unit
              }
            )
          )
        )

        val modelVersionService = mock[ModelVersionService[IO]]
        when(modelVersionService.getNextModelVersion(1)).thenReturn(IO(1L))
        val modelVersionRepository = mock[ModelVersionRepository[IO]]

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
          fetcher = fetcher,
          modelVersionBuilder = versionBuilder,
          servableRepo = null,
          modelVersionRepository = null
        )

        val maybeModel =
          modelManagementService.uploadModel(uploadFile, upload).attempt.unsafeRunSync()
        assert(maybeModel.isRight, maybeModel)
        val rModel = maybeModel.right.get.started
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
        val signature = Signature(
          "testSig",
          NonEmptyList.of(Field.Tensor("input", DT_INT32, TensorShape.Dynamic, None)),
          NonEmptyList.of(Field.Tensor("input", DT_INT32, TensorShape.Dynamic, None))
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
          modelSignature = signature,
          runtime = modelRuntime,
          model = model,
          status = ModelVersionStatus.Released,
          installCommand = None,
          metadata = Map.empty
        )
        val upload = ModelUploadMetadata(
          name = modelName,
          runtime = modelRuntime,
          hostSelectorName = None,
          modelSignature = Some(signature),
          profileTypes = None
        )
        println(upload)

        val modelRepo = mock[ModelRepository[IO]]
        when(modelRepo.update(Matchers.any(classOf[Model]))).thenReturn(IO(1))
        when(modelRepo.get(modelName)).thenReturn(IO(model.some))
        when(modelRepo.get(1)).thenReturn(IO(model.some))

        val storageMock = mock[ModelUnpacker[IO]]
        when(storageMock.unpack(uploadFile))
          .thenReturn(IO(ModelFileStructure.forRoot(Paths.get(".AAAAAAAAA"))))

        val versionService = mock[ModelVersionBuilder[IO]]
        when(versionService.build(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(
          IO(
            DeferredResult(
              modelVersion,
              new Deferred[IO, ModelVersion.Internal] {
                override def get = IO(modelVersion)

                override def complete(a: ModelVersion.Internal) = IO.unit
              }
            )
          )
        )
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
          fetcher = fetcher,
          modelVersionBuilder = versionService,
          servableRepo = null,
          modelVersionRepository = null
        )

        val maybeModel =
          modelManagementService.uploadModel(uploadFile, upload).attempt.unsafeRunSync()
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
          modelSignature = Signature.defaultSignature.some,
          profileTypes = Some(Map("a" -> IMAGE)),
          installCommand = Some("echo hello"),
          metadata = Some(Map("author" -> "me"))
        )

        val res = ModelVersionMetadata.combineMetadata(fetched, uploaded).get

        assert(res.modelName === "upload-name")
        assert(res.runtime === DockerImage("test", "test"))
        assert(res.signature === defaultSignature)
        assert(res.installCommand === Some("echo hello"))
        assert(res.metadata === Map("author" -> "me"))
      }

      it("uploaded and fetched") {
        val signature = Signature(
          "testSig",
          NonEmptyList.of(Field.Tensor("input", DT_INT32, TensorShape.Dynamic, None)),
          NonEmptyList.of(Field.Tensor("output", DT_INT32, TensorShape.Dynamic, None))
        )
        val fetched = Some(
          FetcherResult(
            modelName = "uuuu",
            modelSignature = signature,
            metadata = Map("f" -> "123", "overriden" -> "false")
          )
        )
        val uploaded = ModelUploadMetadata(
          name = "upload-name",
          runtime = DockerImage("test", "test"),
          hostSelectorName = None,
          modelSignature = None,
          profileTypes = Some(Map("a" -> IMAGE)),
          installCommand = Some("echo hello"),
          metadata = Some(Map("author" -> "me", "overriden" -> "true"))
        )
        val res = ModelVersionMetadata.combineMetadata(fetched, uploaded).get
        assert(res.modelName === "upload-name")
        assert(res.runtime === DockerImage("test", "test"))
        assert(res.signature === signature)
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
          modelSignature = Signature.defaultSignature,
          runtime = dummyImage,
          model = appFailedModel,
          status = ModelVersionStatus.Released,
          installCommand = None,
          metadata = Map.empty
        )
        val app = Application(
          id = 1,
          name = "app",
          namespace = None,
          status = Application.Status.Failed,
          statusMessage = None,
          signature = Signature.defaultSignature,
          kafkaStreaming = Nil,
          graph = ApplicationGraph(
            NonEmptyList.of(
              ApplicationStage(
                NonEmptyList.of(ApplicationServable(appFailedVersion, 100)),
                Signature.defaultSignature
              )
            )
          )
        )

        val servableFailedModel = Model(2, "servable-failing")
        val servableFailedVersion = ModelVersion.Internal(
          id = 2,
          image = dummyImage,
          created = Instant.now(),
          finished = Some(Instant.now()),
          modelVersion = 2,
          modelSignature = Signature.defaultSignature,
          runtime = dummyImage,
          model = servableFailedModel,
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
          host = None,
          port = None,
          message = "asd",
          deploymentConfiguration = DeploymentConfiguration.empty
        )
        val okModel = Model(3, "ok")
        val okVersion1 = ModelVersion.Internal(
          id = 3,
          image = dummyImage,
          created = Instant.now(),
          finished = Some(Instant.now()),
          modelVersion = 1,
          modelSignature = Signature.defaultSignature,
          runtime = dummyImage,
          model = okModel,
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
          modelSignature = Signature.defaultSignature,
          runtime = dummyImage,
          model = okModel,
          status = ModelVersionStatus.Released,
          installCommand = None,
          metadata = Map.empty
        )
        val modelRepo = new ModelRepository[IO] {
          override def create(entity: Model): IO[Model] = ???
          override def get(id: Long): IO[Option[Model]] =
            id match {
              case appFailedModel.id      => IO.pure(Some(appFailedModel))
              case servableFailedModel.id => IO.pure(Some(servableFailedModel))
              case okModel.id             => IO.pure(Some(okModel))
              case _                      => IO(None)
            }
          override def all(): IO[Seq[Model]]                = ???
          override def get(name: String): IO[Option[Model]] = ???
          override def update(value: Model): IO[Int]        = ???
          override def delete(id: Long): IO[Int]            = IO.pure(1)
        }
        val appRepo = new ApplicationRepository[IO] {
          override def create(entity: Application): IO[Application] = ???
          override def get(id: Long): IO[Option[Application]]       = ???
          override def get(name: String): IO[Option[Application]]   = ???
          override def update(value: Application): IO[Int]          = ???
          override def delete(id: Long): IO[Int]                    = ???
          override def all(): IO[List[Application]]                 = ???
          override def findVersionUsage(versionIdx: Long): IO[List[Application]] =
            versionIdx match {
              case appFailedModel.id => IO(app :: Nil)
              case _                 => IO.pure(Nil)
            }
          override def findServableUsage(servableName: String): IO[List[Application]] = ???
        }
        val servableRepo = new ServableRepository[IO] {
          override def all(): IO[List[Servable]]                   = ???
          override def upsert(servable: Servable): IO[Servable]    = ???
          override def delete(name: String): IO[Int]               = ???
          override def get(name: String): IO[Option[Servable]]     = ???
          override def get(names: Seq[String]): IO[List[Servable]] = ???
          override def findForModelVersion(versionId: Long): IO[List[Servable]] =
            versionId match {
              case servableFailedVersion.id =>
                println("Here")
                IO.pure(servable :: Nil)
              case _ =>
                println(s"Ok ${versionId}")
                IO.pure(Nil)
            }
        }
        val modelVersionService = new ModelVersionService[IO] {
          override def all(): IO[List[ModelVersion.Internal]]                 = ???
          override def get(id: Long): IO[ModelVersion.Internal]               = ???
          override def get(name: String, version: Long): IO[ModelVersionView] = ???
          override def getNextModelVersion(modelId: Long): IO[Long]           = ???
          override def list: IO[List[ModelVersionView]]                       = ???
          override def listForModel(modelId: Long): IO[List[ModelVersion.Internal]] =
            modelId match {
              case appFailedModel.id      => IO.pure(appFailedVersion :: Nil)
              case servableFailedModel.id => IO.pure(servableFailedVersion :: Nil)
              case okModel.id             => IO.pure(okVersion1 :: okVersion2 :: Nil)
              case _                      => IO.raiseError(new RuntimeException(s"Shouldn't delete model $modelId"))
            }
          override def delete(versionId: Long): IO[Option[ModelVersion.Internal]] =
            versionId match {
              case okVersion1.id => IO.pure(Some(okVersion1))
              case okVersion2.id => IO.pure(Some(okVersion2))
              case _             => IO.raiseError(new RuntimeException(s"Shouldn't delete version $versionId"))
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
          fetcher = null,
          modelVersionBuilder = null,
          modelVersionRepository = null
        )

        val result = modelService.deleteModel(okModel.id).unsafeRunSync()
        assert(result.name == okModel.name)

        val failedApp = modelService.deleteModel(appFailedModel.id).attempt.unsafeRunSync()
        assert(failedApp.left.get.isInstanceOf[DomainError.InvalidRequest], failedApp)

        val failedServable =
          modelService.deleteModel(servableFailedModel.id).attempt.unsafeRunSync()
        assert(failedServable.left.get.isInstanceOf[DomainError.InvalidRequest], failedServable)
      }
    }
  }
}
