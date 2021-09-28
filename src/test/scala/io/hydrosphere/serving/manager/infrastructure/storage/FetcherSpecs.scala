package io.hydrosphere.serving.manager.infrastructure.storage

import cats.data.NonEmptyList
import cats.effect.IO
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.contract.DataType.{DT_FLOAT, DT_INT64}
import io.hydrosphere.serving.manager.domain.contract.{DataType, Field, Signature, TensorShape}
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers._
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.keras.KerasFetcher
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.spark.SparkModelFetcher
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.tensorflow.TensorflowModelFetcher
import org.scalatest.enablers.Definition.definitionOfOption

import java.nio.file.Path

class FetcherSpecs extends GenericUnitTest {
  implicit val ops: LocalStorageOps[IO] = StorageOps.default[IO]

  def getModel(modelName: String): Path = {
    val path = getTestResourcePath("test_models")
    val res  = path.resolve(modelName)
    res
  }

  describe("Combined fetcher") {
    it("should not fail if underlying fetcher fails") {
      val successfulFetcher = new ModelFetcher[IO] {
        override def fetch(path: Path): IO[Option[FetcherResult]] = IO(None)
      }
      val failingFetcher = new ModelFetcher[IO] {
        override def fetch(path: Path): IO[Option[FetcherResult]] =
          IO.raiseError(new Exception("hello there!"))
      }
      val combined = ModelFetcher.combine(successfulFetcher :: failingFetcher :: Nil)
      ioAssert {
        combined.fetch(getModel("scikit_model")).map(x => assert(x.isEmpty))
      }
    }
  }

  describe("Fallback") {
    it("should parse contract proto message") {
      ioAssert {
        val fetcher: FallbackContractFetcher[IO]   = new FallbackContractFetcher(ops)
        val model: Path                            = getModel("scikit_model")
        val fetchResult: IO[Option[FetcherResult]] = fetcher.fetch(model)

        fetchResult.map { model =>
          model shouldBe defined
          assert(model.get.modelName === "scikit_model")
        }
      }
    }
  }

  describe("Spark model fetcher") {
    it("should parse correct spark model") {
      ioAssert {
        val fetcher     = new SparkModelFetcher(ops)
        val model       = getModel("spark_model")
        val fetchResult = fetcher.fetch(model)

        fetchResult.map { model =>
          model shouldBe defined
          assert(model.get.modelName === "spark_model")
          assert(
            model.get.metadata === Map(
              "sparkml.class"        -> "org.apache.spark.ml.PipelineModel",
              "sparkml.timestamp"    -> "1497440372794",
              "sparkml.sparkVersion" -> "2.1.1",
              "sparkml.uid"          -> "PipelineModel_4ccbbca3d107857d3ed8"
            )
          )
        }
      }
    }
  }

  describe("Tensorflow model fetcher") {
    it("should parse correct tensorflow model") {
      ioAssert {
        val expectedSigs =
          Signature(
            "serving_default",
            NonEmptyList.of(
              Field.Tensor(
                "images",
                DT_FLOAT,
                TensorShape.mat(-1, 784),
                None
              )
            ),
            NonEmptyList.of(
              Field.Tensor(
                "labels",
                DT_INT64,
                TensorShape.vector(-1),
                None
              ),
              Field.Tensor(
                "labels2",
                DT_INT64,
                TensorShape.vector(-1),
                None
              ),
              Field.Tensor(
                "random",
                DT_FLOAT,
                TensorShape.mat(2, 3),
                None
              )
            )
          )

        val fetcher = new TensorflowModelFetcher(ops)
        fetcher.fetch(getModel("tensorflow_model")).map { modelResult =>
          modelResult shouldBe defined
          val model = modelResult.get
          assert(model.modelSignature === expectedSigs)
          assert(
            model.metadata === Map(
              "tensorflow.metaGraph[0].tagsCount"            -> "1",
              "tensorflow.metaGraph[0].tensorflowGitVersion" -> "b'unknown'",
              "tensorflow.metaGraph[0].strippedDefaultAttrs" -> "false",
              "tensorflow.metaGraph[0].serializedSize"       -> "55589",
              "tensorflow.metaGraph[0].assetFilesCount"      -> "0",
              "tensorflow.metaGraph[0].signatureCount"       -> "1",
              "tensorflow.metaGraph[0].tensorflowVersion"    -> "1.1.0",
              "tensorflow.metaGraphsCount"                   -> "1",
              "tensorflow.metaGraph[0].collectionsCount"     -> "4"
            )
          )
        }
      }
    }
  }

  describe("KerasFetcher") {
    it("should parse sequential model from .h5") {
      ioAssert {
        val expectedSignature = Signature(
          "Predict",
          NonEmptyList.of(
            Field.Tensor("flatten_1", DT_FLOAT, TensorShape.mat(-1, 28, 28), None)
          ),
          NonEmptyList.of(
            Field.Tensor("dense_3", DT_FLOAT, TensorShape.mat(-1, 10), None)
          )
        )
        val fetcher = new KerasFetcher[IO](ops)
        val fres    = fetcher.fetch(getModel("keras_model/sequential"))
        fres.map { fetchResult =>
          assert(fetchResult.isDefined, fetchResult)
          val metadata = fetchResult.get
          assert(metadata.modelName === "keras_fashion_mnist")
          assert(metadata.modelSignature === expectedSignature)
          assert(metadata.metadata === Map())
        }
      }
    }

    it("should parse functional model from .h5") {
      ioAssert {
        val expectedSignature = Signature(
          "Predict",
          NonEmptyList.of(
            Field.Tensor("input_7", DT_FLOAT, TensorShape.mat(-1, 784))
          ),
          NonEmptyList.of(
            Field.Tensor("dense_20", DataType.DT_FLOAT, TensorShape.mat(-1, 10)),
            Field.Tensor("dense_21", DataType.DT_FLOAT, TensorShape.mat(-1, 10))
          )
        )

        val fetcher = new KerasFetcher[IO](ops)
        val fres    = fetcher.fetch(getModel("keras_model/functional"))
        fres.map { fetchResult =>
          assert(fetchResult.isDefined, fetchResult)
          val metadata = fetchResult.get
          println(metadata)
          assert(metadata.modelName === "nonseq_model")
          assert(metadata.modelSignature === expectedSignature)
          assert(metadata.metadata === Map())
        }
      }
    }
  }

  describe("Default fetcher") {
    it("should parse tensorflow model") {
      ioAssert {
        val defaultFetcher = ModelFetcher.default[IO]
        val model          = getModel("tensorflow_model")
        val fetcherResult  = defaultFetcher.fetch(model)

        fetcherResult.map(model => assert(model.isDefined, model))
      }
    }
  }
}
