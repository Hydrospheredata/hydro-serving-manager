package io.hydrosphere.serving.manager.domain.tensor

import com.google.protobuf.ByteString
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.proto.contract.signature.{ModelSignature, SignatureBuilder}
import io.hydrosphere.serving.proto.contract.tensor.{MapTensorData, Tensor, TensorShape}
import io.hydrosphere.serving.proto.contract.tensor.definitions.{Shape, TypedTensorFactory}
import io.hydrosphere.serving.proto.contract.types.DataType
import io.hydrosphere.serving.proto.contract.tensor.builders.SignatureBuilder
import io.hydrosphere.serving.proto.contract.tensor.generators.TensorExampleGenerator

class TensorExampleGeneratorSpec extends GenericUnitTest {
  val fooString = ByteString.copyFromUtf8("foo")

  describe("TensorExampleGenerator") {
    describe("should generate correct example") {
      it("when scalar flat signature") {
        val sig1 = ModelSignature(
          "sig1",
          List(
            SignatureBuilder.simpleTensorModelField("in1", DataType.DT_STRING, Shape.scalar)
          ),
          List(
            SignatureBuilder.simpleTensorModelField("out1", DataType.DT_DOUBLE, Shape.vector(-1))
          )
        )

        val expected = Map(
          "in1" -> TypedTensorFactory.create(
            Tensor(
              dtype = DataType.DT_STRING,
              tensorShape = Shape.scalar.toProto,
              stringVal = List(fooString)
            )
          )
        )

        val generated = TensorExampleGenerator(sig1).inputs
        assert(generated === expected)
      }

      it("when vector flat signature") {
        val sig1 = ModelSignature(
          "sig1",
          List(
            SignatureBuilder
              .simpleTensorModelField("in1", DataType.DT_STRING, Shape.vector(-1)),
            SignatureBuilder.simpleTensorModelField("in2", DataType.DT_INT32, Shape.vector(3))
          ),
          List(
            SignatureBuilder
              .simpleTensorModelField("out1", DataType.DT_DOUBLE, Shape.vector(-1))
          )
        )

        val expected = Map(
          "in1" -> TypedTensorFactory.create(
            Tensor(
              dtype = DataType.DT_STRING,
              tensorShape = Shape.vector(-1).toProto,
              stringVal = List(fooString)
            )
          ),
          "in2" -> TypedTensorFactory.create(
            Tensor(
              dtype = DataType.DT_INT32,
              tensorShape = Shape.vector(3).toProto,
              intVal = List(1, 1, 1)
            )
          )
        )

        val generated = TensorExampleGenerator(sig1).inputs
        assert(generated === expected)
      }

      //TODO: complex
//      it("when nested singular signature") {
//        val sig1 = ModelSignature(
//          "sig1",
//          List(
//            SignatureBuilder.complexField(
//              "in1",
//              Shape.scalar.toProto,
//              Seq(
//                SignatureBuilder
//                  .simpleTensorModelField("a", DataType.DT_STRING, Shape.scalar),
//                SignatureBuilder.simpleTensorModelField("b", DataType.DT_STRING, Shape.scalar)
//              )
//            )
//          ),
//          List(
//            SignatureBuilder
//              .simpleTensorModelField("out1", DataType.DT_DOUBLE, Shape.vector(-1))
//          )
//        )
//
//        val expected = Map(
//          "in1" ->
//            TypedTensorFactory.create(
//              Tensor(
//                dtype = DataType.DT_MAP,
//                tensorShape = Shape.scalar.toProto,
//                mapVal = Seq(
//                  MapTensorData(
//                    Map(
//                      "a" -> Tensor(
//                        DataType.DT_STRING,
//                        Shape.scalar.toProto,
//                        stringVal = List(fooString)
//                      ),
//                      "b" -> Tensor(
//                        DataType.DT_STRING,
//                        Shape.scalar.toProto,
//                        stringVal = List(fooString)
//                      )
//                    )
//                  )
//                )
//              )
//            )
//        )
//
//        val generated = TensorExampleGenerator(sig1).inputs
//        assert(generated === expected)
//      }

      // TODO: complex
//      it("when nested vector signature") {
//        val sig1 = ModelSignature(
//          "sig1",
//          List(
//            SignatureBuilder.complexField(
//              "in1",
//              Shape.vector(3).toProto,
//              Seq(
//                SignatureBuilder
//                  .simpleTensorModelField("a", DataType.DT_STRING, Shape.scalar),
//                SignatureBuilder.simpleTensorModelField("b", DataType.DT_STRING, Shape.scalar)
//              )
//            )
//          ),
//          List(
//            SignatureBuilder
//              .simpleTensorModelField("out1", DataType.DT_DOUBLE, Shape.vector(-1))
//          )
//        )
//
//        val expected = Map(
//          "in1" -> TypedTensorFactory.create(
//            Tensor(
//              dtype = DataType.DT_MAP,
//              tensorShape = Shape.vector(3).toProto,
//              mapVal = Seq(
//                MapTensorData(
//                  Map(
//                    "a" -> Tensor(
//                      DataType.DT_STRING,
//                      Shape.scalar.toProto,
//                      stringVal = List(fooString)
//                    ),
//                    "b" -> Tensor(
//                      DataType.DT_STRING,
//                      Shape.scalar.toProto,
//                      stringVal = List(fooString)
//                    )
//                  )
//                ),
//                MapTensorData(
//                  Map(
//                    "a" -> Tensor(
//                      DataType.DT_STRING,
//                      Shape.scalar.toProto,
//                      stringVal = List(fooString)
//                    ),
//                    "b" -> Tensor(
//                      DataType.DT_STRING,
//                      Shape.scalar.toProto,
//                      stringVal = List(fooString)
//                    )
//                  )
//                ),
//                MapTensorData(
//                  Map(
//                    "a" -> Tensor(
//                      DataType.DT_STRING,
//                      Shape.scalar.toProto,
//                      stringVal = List(fooString)
//                    ),
//                    "b" -> Tensor(
//                      DataType.DT_STRING,
//                      Shape.scalar.toProto,
//                      stringVal = List(fooString)
//                    )
//                  )
//                )
//              )
//            )
//          )
//        )
//
//        val generated = TensorExampleGenerator(sig1).inputs
//        assert(generated === expected)
//      }

      it("should generate None shape") {
        val tensorShape = Shape.AnyShape
        val resultOpt   = TensorExampleGenerator.generateTensor(tensorShape, DataType.DT_FLOAT)
        assert(resultOpt.isDefined)
        val result = resultOpt.get
        assert(result.shape.toProto === None)
        assert(result.data === Seq(1.0))
      }

      it("should generate correct scalar") {
        val tensorShape = Shape.LocalShape(Seq.empty)
        val resultOpt   = TensorExampleGenerator.generateTensor(tensorShape, DataType.DT_FLOAT)
        assert(resultOpt.isDefined)
        val result = resultOpt.get
        assert(result.shape.toProto === Some(TensorShape(Seq.empty)))
        assert(result.data === Seq(1.0))
      }

      it("should generate correct [-1] vector") {
        val tensorShape = Shape.LocalShape(Seq(-1))
        val resultOpt   = TensorExampleGenerator.generateTensor(tensorShape, DataType.DT_FLOAT)
        assert(resultOpt.isDefined)
        val result = resultOpt.get
        assert(result.shape === tensorShape)
        assert(result.data === Seq(1.0))
      }

      it("should generate correct [1] vector") {
        val tensorShape = Shape.LocalShape(Seq(1))
        val resultOpt   = TensorExampleGenerator.generateTensor(tensorShape, DataType.DT_FLOAT)
        assert(resultOpt.isDefined)
        val result = resultOpt.get
        assert(result.shape === tensorShape)
        assert(result.data === Seq(1.0))
      }

      it("should generate correct [2] vector") {
        val tensorShape = Shape.LocalShape(Seq(2))
        val resultOpt   = TensorExampleGenerator.generateTensor(tensorShape, DataType.DT_FLOAT)
        assert(resultOpt.isDefined)
        val result = resultOpt.get
        assert(result.shape === tensorShape)
        assert(result.data === Seq(1.0, 1.0))
      }

      it("should generate correct [-1,1] vector") {
        val tensorShape = Shape.LocalShape(Seq(-1, 1))
        val resultOpt   = TensorExampleGenerator.generateTensor(tensorShape, DataType.DT_FLOAT)
        assert(resultOpt.isDefined)
        val result = resultOpt.get
        assert(result.shape === tensorShape)
        assert(result.data === Seq(1.0))
      }

      it("should generate correct [-1, 4] vector") {
        val tensorShape = Shape.LocalShape(Seq(-1, 4))
        val resultOpt   = TensorExampleGenerator.generateTensor(tensorShape, DataType.DT_FLOAT)
        assert(resultOpt.isDefined)
        val result = resultOpt.get
        assert(result.shape === tensorShape)
        assert(result.data === Seq(1.0, 1.0, 1.0, 1.0))
      }

      it("should generate correct [1, 4] vector") {
        val tensorShape = Shape.LocalShape(Seq(1, 4))
        val resultOpt   = TensorExampleGenerator.generateTensor(tensorShape, DataType.DT_FLOAT)
        assert(resultOpt.isDefined)
        val result = resultOpt.get
        assert(result.shape === tensorShape)
        assert(result.data === Seq(1.0, 1.0, 1.0, 1.0))
      }

      it("should generate correct [4, 4] vector") {
        val tensorShape = Shape.LocalShape(Seq(4, 4))
        val resultOpt   = TensorExampleGenerator.generateTensor(tensorShape, DataType.DT_FLOAT)
        assert(resultOpt.isDefined)
        val result = resultOpt.get
        assert(result.shape === tensorShape)
        assert(
          result.data === Seq(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0,
            1.0, 1.0)
        )
      }
    }
  }
}
