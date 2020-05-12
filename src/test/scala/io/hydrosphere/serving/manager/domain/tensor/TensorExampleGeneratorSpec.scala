package io.hydrosphere.serving.manager.domain.tensor

import cats.data.NonEmptyList
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.contract.{DataType, Field, Signature, TensorShape}

class TensorExampleGeneratorSpec extends GenericUnitTest {
  val fooString = "foo"

  describe("TensorExampleGenerator") {
    describe("should generate correct example") {
      it("when scalar flat signature") {
        val sig1 = Signature(
          "sig1",
          NonEmptyList.of(Field.Tensor("in1", DataType.DT_STRING, TensorShape.scalar, None)),
          NonEmptyList.of(Field.Tensor("out1", DataType.DT_STRING, TensorShape.varVector, None))
        )

        val expected = Map(
          "in1" -> StringTensor(TensorShape.scalar, Seq(fooString))
        )

        val generated = TensorExampleGenerator(sig1).inputs
        assert(generated === expected)
      }

      it("when vector flat signature") {
        val sig1 = Signature(
          "sig1",
          NonEmptyList.of(
            Field.Tensor("in1", DataType.DT_STRING, TensorShape.varVector, None),
            Field.Tensor("in2", DataType.DT_INT32, TensorShape.vector(3), None)
          ),
          NonEmptyList.of(
            Field.Tensor("out1", DataType.DT_DOUBLE, TensorShape.varVector, None)
          )
        )

        val expected = Map(
          "in1" ->
            StringTensor(
              TensorShape.varVector,
              Seq(fooString)
            ),
          "in2" ->
            Int32Tensor(
              TensorShape.vector(3),
              List(1, 1, 1)
            )
        )

        val generated = TensorExampleGenerator(sig1).inputs
        assert(generated === expected)
      }

      it("when nested singular signature") {
        val sig1 = Signature(
          "sig1",
          NonEmptyList.of(
            Field.Map(
              "in1",
              List(
                Field.Tensor("a", DataType.DT_STRING, TensorShape.scalar, None),
                Field.Tensor("b", DataType.DT_STRING, TensorShape.scalar, None)
              ),
              TensorShape.scalar
            )
          ),
          NonEmptyList.of(Field.Tensor("out1", DataType.DT_DOUBLE, TensorShape.varVector, None))
        )

        val expected = Map(
          "in1" ->
            MapTensor(
              shape = TensorShape.scalar,
              data = Seq(
                Map(
                  "a" -> StringTensor(
                    TensorShape.scalar,
                    List(fooString)
                  ),
                  "b" -> StringTensor(
                    TensorShape.scalar,
                    List(fooString)
                  )
                )
              )
            )
        )

        val generated = TensorExampleGenerator(sig1).inputs
        assert(generated === expected)
      }

      it("when nested vector signature") {
        val sig1 = Signature(
          "sig1",
          NonEmptyList.of(
            Field.Map(
              "in1",
              List(
                Field.Tensor("a", DataType.DT_STRING, TensorShape.scalar, None),
                Field.Tensor("b", DataType.DT_STRING, TensorShape.scalar, None)
              ),
              TensorShape.vector(3)
            )
          ),
          NonEmptyList.of(
            Field.Tensor("out1", DataType.DT_DOUBLE, TensorShape.varVector, None)
          )
        )

        val expected = Map(
          "in1" ->
            MapTensor(
              TensorShape.vector(3),
              Seq(
                Map(
                  "a" -> StringTensor(
                    TensorShape.scalar,
                    List(fooString)
                  ),
                  "b" -> StringTensor(
                    TensorShape.scalar,
                    List(fooString)
                  )
                ),
                Map(
                  "a" -> StringTensor(
                    TensorShape.scalar,
                    List(fooString)
                  ),
                  "b" -> StringTensor(
                    TensorShape.scalar,
                    List(fooString)
                  )
                ),
                Map(
                  "a" -> StringTensor(
                    TensorShape.scalar,
                    List(fooString)
                  ),
                  "b" -> StringTensor(
                    TensorShape.scalar,
                    List(fooString)
                  )
                )
              )
            )
        )

        val generated = TensorExampleGenerator(sig1).inputs
        assert(generated === expected)
      }

      it("should generate None shape") {
        val tensorShape = TensorShape.Dynamic
        val resultOpt   = TensorExampleGenerator.generateTensor(tensorShape, DataType.DT_FLOAT)
        assert(resultOpt.isDefined)
        val result = resultOpt.get
        assert(result.shape === tensorShape)
        assert(result.data === Seq(1.0))
      }

      it("should generate correct scalar") {
        val tensorShape = TensorShape.scalar
        val resultOpt   = TensorExampleGenerator.generateTensor(tensorShape, DataType.DT_FLOAT)
        assert(resultOpt.isDefined)
        val result = resultOpt.get
        assert(result.shape === tensorShape)
        assert(result.data === Seq(1.0))
      }

      it("should generate correct [-1] vector") {
        val tensorShape = TensorShape.varVector
        val resultOpt   = TensorExampleGenerator.generateTensor(tensorShape, DataType.DT_FLOAT)
        assert(resultOpt.isDefined)
        val result = resultOpt.get
        assert(result.shape === tensorShape)
        assert(result.data === Seq(1.0))
      }

      it("should generate correct [1] vector") {
        val tensorShape = TensorShape.vector(1)
        val resultOpt   = TensorExampleGenerator.generateTensor(tensorShape, DataType.DT_FLOAT)
        assert(resultOpt.isDefined)
        val result = resultOpt.get
        assert(result.shape === tensorShape)
        assert(result.data === Seq(1.0))
      }

      it("should generate correct [2] vector") {
        val tensorShape = TensorShape.vector(2)
        val resultOpt   = TensorExampleGenerator.generateTensor(tensorShape, DataType.DT_FLOAT)
        assert(resultOpt.isDefined)
        val result = resultOpt.get
        assert(result.shape === tensorShape)
        assert(result.data === Seq(1.0, 1.0))
      }

      it("should generate correct [-1,1] vector") {
        val tensorShape = TensorShape.mat(-1, 1)
        val resultOpt   = TensorExampleGenerator.generateTensor(tensorShape, DataType.DT_FLOAT)
        assert(resultOpt.isDefined)
        val result = resultOpt.get
        assert(result.shape === tensorShape)
        assert(result.data === Seq(1.0))
      }

      it("should generate correct [-1, 4] vector") {
        val tensorShape = TensorShape.mat(-1, 4)
        val resultOpt   = TensorExampleGenerator.generateTensor(tensorShape, DataType.DT_FLOAT)
        assert(resultOpt.isDefined)
        val result = resultOpt.get
        assert(result.shape === tensorShape)
        assert(result.data === Seq(1.0, 1.0, 1.0, 1.0))
      }

      it("should generate correct [1, 4] vector") {
        val tensorShape = TensorShape.mat(1, 4)
        val resultOpt   = TensorExampleGenerator.generateTensor(tensorShape, DataType.DT_FLOAT)
        assert(resultOpt.isDefined)
        val result = resultOpt.get
        assert(result.shape === tensorShape)
        assert(result.data === Seq(1.0, 1.0, 1.0, 1.0))
      }

      it("should generate correct [4, 4] vector") {
        val tensorShape = TensorShape.mat(4, 4)
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
