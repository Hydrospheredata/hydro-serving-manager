package io.hydrosphere.serving.manager.domain

import java.nio.file.{Path, Paths}
import java.time.Instant

import cats.MonadError
import cats.effect.IO
import cats.effect.concurrent.Deferred
import cats.syntax.option._
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.contract.model_field.ModelField
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.api.http.controller.model.ModelUploadMetadata
import io.hydrosphere.serving.manager.data_profile_types.DataProfileType
import io.hydrosphere.serving.manager.domain.host_selector.HostSelectorRepository
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.{Model, ModelRepository, ModelService, ModelVersionMetadata}
import io.hydrosphere.serving.manager.domain.model_build.ModelVersionBuilder
import io.hydrosphere.serving.manager.domain.model_version._
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.{FetcherResult, ModelFetcher}
import io.hydrosphere.serving.manager.infrastructure.storage.{ModelFileStructure, ModelUnpacker}
import io.hydrosphere.serving.manager.util.DeferredResult
import io.hydrosphere.serving.tensorflow.TensorShape
import io.hydrosphere.serving.tensorflow.types.DataType
import org.mockito.Matchers

class ModelServiceSpec extends GenericUnitTest {
  describe("Model service") {
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
        val modelVersion = ModelVersion(
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
          DeferredResult(modelVersion, new Deferred[IO, ModelVersion] {
            override def get = IO(modelVersion)

            override def complete(a: ModelVersion) = IO.unit
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
          modelRepository = modelRepo,
          modelVersionService = modelVersionService,
          modelVersionRepository = modelVersionRepository,
          storageService = storageMock,
          appRepo = null,
          hostSelectorRepository = selectorRepo,
          fetcher = fetcher,
          modelVersionBuilder = versionBuilder
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
        val modelVersion = ModelVersion(
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
        val modelVersionMetadata = ModelVersionMetadata(
          modelName = modelName,
          contract = contract,
          profileTypes = Map.empty,
          runtime = modelRuntime,
          hostSelector = None,
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
          DeferredResult(modelVersion, new Deferred[IO, ModelVersion] {
            override def get = IO(modelVersion)

            override def complete(a: ModelVersion) = IO.unit
          })
        ))
        val fetcher = new ModelFetcher[IO] {
          override def fetch(path: Path) = IO(None)
        }

        val modelManagementService = ModelService[IO]()(
          MonadError[IO, Throwable],
          modelRepository = modelRepo,
          modelVersionService = null,
          modelVersionRepository = null,
          storageService = storageMock,
          appRepo = null,
          hostSelectorRepository = null,
          fetcher = fetcher,
          modelVersionBuilder = versionService
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
        assert(res.profileTypes === Map("a" -> DataProfileType.IMAGE))
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
        assert(res.profileTypes === Map("a" -> DataProfileType.IMAGE))
      }
    }
  }
}