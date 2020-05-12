package io.hydrosphere.serving.manager.infrastructure.storage

import cats.data.NonEmptyList
import cats.effect.IO
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.contract._
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers._
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.keras.KerasFetcher
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.spark.SparkModelFetcher
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.tensorflow.TensorflowModelFetcher

class FetcherSpecs extends GenericUnitTest {
  val ops = StorageOps.default[IO]

  def getModel(modelName: String) =
    getTestResourcePath("test_models").resolve(modelName)

  describe("Fallback") {
    it("should parse contract proto message") {
      ioAssert {
        val fetcher = new FallbackContractFetcher(ops)
        fetcher.fetch(getModel("scikit_model")).map { model =>
          model shouldBe defined
          assert(model.get.modelName === "scikit_model")
        }
      }
    }
  }

  describe("Spark model fetcher") {
    it("should parse correct spark model") {
      ioAssert {
        val fetcher = new SparkModelFetcher(ops)
        fetcher.fetch(getModel("spark_model")).map { model =>
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
        val expectedSigs = Signature(
          "serving_default",
          NonEmptyList.of(
            Field.Tensor(
              "images",
              DataType.DT_FLOAT,
              TensorShape.Static(List(-1, 784)),
              None
            )
          ),
          NonEmptyList.of(
            Field.Tensor(
              "labels",
              DataType.DT_INT64,
              TensorShape.Static(List(-1)),
              None
            ),
            Field.Tensor(
              "labels2",
              DataType.DT_INT64,
              TensorShape.Static(List(-1)),
              None
            ),
            Field.Tensor(
              "random",
              DataType.DT_FLOAT,
              TensorShape.Static(List(2, 3)),
              None
            )
          )
        )
        val fetcher = new TensorflowModelFetcher(ops)
        fetcher.fetch(getModel("tensorflow_model")).map { modelResult =>
          modelResult shouldBe defined
          val model = modelResult.get
          assert(model.modelContract.predict === expectedSigs)
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

  describe("ONNX fetcher") {
    it("should parse ONNX model") {
      ioAssert {
        val expectedContract = Contract(
          Signature(
            "Predict",
            NonEmptyList.of(
              Field.Tensor(
                "Input73",
                DataType.DT_FLOAT,
                TensorShape.Static(List(1, 1, 28, 28)),
                None
              )
            ),
            NonEmptyList.of(
              Field.Tensor(
                "Plus422_Output_0",
                DataType.DT_FLOAT,
                TensorShape.Static(List(1, 10)),
                None
              )
            )
          )
        )

        val fetcher = new ONNXFetcher(ops)
        fetcher.fetch(getModel("onnx_mnist")).map { fetchResult =>
          assert(fetchResult.isDefined, fetchResult)
          val metadata = fetchResult.get
          println(metadata)
          assert(metadata.modelName === "mnist")
          assert(metadata.modelContract === expectedContract)
          assert(
            metadata.metadata === Map(
              "onnx.producerVersion" -> "2.4",
              "onnx.producerName"    -> "CNTK",
              "onnx.modelVersion"    -> "1",
              "onnx.irVersion"       -> "3"
            )
          )
        }
      }
    }
  }

  describe("KerasFetcher") {
    it("should parse sequential model from .h5") {
      ioAssert {
        val expectedContract = Contract(
          Signature(
            "Predict",
            NonEmptyList.of(
              Field.Tensor(
                "flatten_1",
                DataType.DT_FLOAT,
                TensorShape.Static(List(-1, 28, 28)),
                None
              )
            ),
            NonEmptyList.of(
              Field.Tensor(
                "dense_3",
                DataType.DT_FLOAT,
                TensorShape.Static(List(-1, 10)),
                None
              )
            )
          )
        )
        val fetcher = new KerasFetcher[IO](ops)
        val fres    = fetcher.fetch(getModel("keras_model/sequential"))
        fres.map { fetchResult =>
          assert(fetchResult.isDefined, fetchResult)
          val metadata = fetchResult.get
          println(metadata)
          assert(metadata.modelName === "keras_fashion_mnist")
          assert(metadata.modelContract === expectedContract)
          assert(metadata.metadata === Map())
        }
      }
    }

    it("should parse functional model from .h5") {
      ioAssert {
        val expectedContract = Contract(
          Signature(
            "Predict",
            NonEmptyList.of(
              Field.Tensor(
                "input_7",
                DataType.DT_FLOAT,
                TensorShape.Static(List(-1, 784)),
                None
              )
            ),
            NonEmptyList.of(
              Field.Tensor(
                "dense_20",
                DataType.DT_FLOAT,
                TensorShape.Static(List(-1, 10)),
                None
              ),
              Field.Tensor(
                "dense_21",
                DataType.DT_FLOAT,
                TensorShape.Static(List(-1, 10)),
                None
              )
            )
          )
        )
        val fetcher = new KerasFetcher[IO](ops)
        val fres    = fetcher.fetch(getModel("keras_model/functional"))
        fres.map { fetchResult =>
          assert(fetchResult.isDefined, fetchResult)
          val metadata = fetchResult.get
          println(metadata)
          assert(metadata.modelName === "nonseq_model")
          assert(metadata.modelContract === expectedContract)
          assert(metadata.metadata === Map())
        }
      }
    }
  }

  describe("Default fetcher") {
    it("should parse tensorflow model") {
      ioAssert {
        val defaultFetcher = ModelFetcher.default[IO](ops)
        defaultFetcher.fetch(getModel("tensorflow_model")).map { model =>
          assert(model.isDefined, model)
        }
      }
    }
  }
}
