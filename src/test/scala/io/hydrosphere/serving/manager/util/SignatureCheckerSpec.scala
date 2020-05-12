package io.hydrosphere.serving.manager.util

import cats.data.NonEmptyList
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.contract.ops.ModelSignatureOps
import io.hydrosphere.serving.manager.domain.contract.{DataType, Field, Signature, TensorShape}

class SignatureCheckerSpec extends GenericUnitTest {
  describe("SignatureChecker") {
    it("should append when (String,String -> String,String)") {
      val sig1 = Signature(
        "sig1",
        NonEmptyList.of(
          Field.Tensor("in1", DataType.DT_STRING, TensorShape.scalar)
        ),
        NonEmptyList.of(
          Field.Tensor("out1", DataType.DT_STRING, TensorShape.scalar)
        )
      )
      val sig2 = Signature(
        "sig2",
        NonEmptyList.of(
          Field.Tensor("out1", DataType.DT_STRING, TensorShape.scalar)
        ),
        NonEmptyList.of(
          Field.Tensor("out2", DataType.DT_STRING, TensorShape.scalar)
        )
      )
      assert(ModelSignatureOps.append(sig1, sig2).isRight)
    }

    it("should append when (Double[5],Double[5] -> Double[5],Double[5])") {
      val sig1 = Signature(
        "sig1",
        NonEmptyList.of(
          Field.Tensor("in1", DataType.DT_DOUBLE, TensorShape.vector(5))
        ),
        NonEmptyList.of(
          Field.Tensor("out1", DataType.DT_DOUBLE, TensorShape.vector(5))
        )
      )
      val sig2 = Signature(
        "sig2",
        NonEmptyList.of(
          Field.Tensor("out1", DataType.DT_DOUBLE, TensorShape.vector(5))
        ),
        NonEmptyList.of(
          Field.Tensor("out2", DataType.DT_DOUBLE, TensorShape.vector(5))
        )
      )
      assert(ModelSignatureOps.append(sig1, sig2).isRight)
    }

    it("should append when (Int32[3] -> Int32[-1])") {
      val sig1 = Signature(
        "sig1",
        NonEmptyList.of(
          Field.Tensor("in1", DataType.DT_INT32, TensorShape.vector(3))
        ),
        NonEmptyList.of(
          Field.Tensor("out1", DataType.DT_INT32, TensorShape.vector(3))
        )
      )
      val sig2 = Signature(
        "sig2",
        NonEmptyList.of(
          Field.Tensor("out1", DataType.DT_INT32, TensorShape.vector(-1))
        ),
        NonEmptyList.of(
          Field.Tensor("out2", DataType.DT_INT32, TensorShape.vector(-1))
        )
      )
      assert(ModelSignatureOps.append(sig1, sig2).isRight)
    }

    it("should append when (Double[5, 2] -> Double[5, 2])") {
      val sig1 = Signature(
        "sig1",
        NonEmptyList.of(
          Field.Tensor("in1", DataType.DT_DOUBLE, TensorShape.mat(5, 2))
        ),
        NonEmptyList.of(
          Field.Tensor("out1", DataType.DT_DOUBLE, TensorShape.mat(5, 2))
        )
      )
      val sig2 = Signature(
        "sig2",
        NonEmptyList.of(
          Field.Tensor("out1", DataType.DT_DOUBLE, TensorShape.mat(5, 2))
        ),
        NonEmptyList.of(
          Field.Tensor("out2", DataType.DT_DOUBLE, TensorShape.mat(5, 2))
        )
      )
      assert(ModelSignatureOps.append(sig1, sig2).isRight)
    }

    it("should append when(Double[5, 2] -> Double[5, -1])") {
      val sig1 = Signature(
        "sig1",
        NonEmptyList.of(
          Field.Tensor("in1", DataType.DT_DOUBLE, TensorShape.mat(5, 2))
        ),
        NonEmptyList.of(
          Field.Tensor("out1", DataType.DT_DOUBLE, TensorShape.mat(5, 2))
        )
      )
      val sig2 = Signature(
        "sig2",
        NonEmptyList.of(
          Field.Tensor("out1", DataType.DT_DOUBLE, TensorShape.mat(5, -1))
        ),
        NonEmptyList.of(
          Field.Tensor("out2", DataType.DT_DOUBLE, TensorShape.mat(5, -1))
        )
      )
      assert(ModelSignatureOps.append(sig1, sig2).isRight)
    }

    it("shouldn't append (String -> Int32)") {
      val sig1 = Signature(
        "sig1",
        NonEmptyList.of(
          Field.Tensor("in1", DataType.DT_STRING, TensorShape.scalar)
        ),
        NonEmptyList.of(
          Field.Tensor("out1", DataType.DT_STRING, TensorShape.scalar)
        )
      )
      val sig2 = Signature(
        "sig2",
        NonEmptyList.of(
          Field.Tensor("out1", DataType.DT_INT32, TensorShape.scalar)
        ),
        NonEmptyList.of(
          Field.Tensor("out2", DataType.DT_INT32, TensorShape.scalar)
        )
      )
      assert(ModelSignatureOps.append(sig1, sig2).isLeft)
    }

    it("shouldn't append (String[3] -> String[4])") {
      val sig1 = Signature(
        "sig1",
        NonEmptyList.of(
          Field.Tensor("in1", DataType.DT_STRING, TensorShape.vector(3))
        ),
        NonEmptyList.of(
          Field.Tensor("out1", DataType.DT_STRING, TensorShape.vector(3))
        )
      )
      val sig2 = Signature(
        "sig2",
        NonEmptyList.of(
          Field.Tensor("out1", DataType.DT_INT32, TensorShape.vector(4))
        ),
        NonEmptyList.of(
          Field.Tensor("out2", DataType.DT_INT32, TensorShape.vector(4))
        )
      )
      assert(ModelSignatureOps.append(sig1, sig2).isLeft)
    }

    it("shouldn't append (Double[4] -> Double[3])") {
      val sig1 = Signature(
        "sig1",
        NonEmptyList.of(
          Field.Tensor("in1", DataType.DT_DOUBLE, TensorShape.vector(4))
        ),
        NonEmptyList.of(
          Field.Tensor("out1", DataType.DT_DOUBLE, TensorShape.vector(4))
        )
      )
      val sig2 = Signature(
        "sig2",
        NonEmptyList.of(
          Field.Tensor("out1", DataType.DT_DOUBLE, TensorShape.vector(3))
        ),
        NonEmptyList.of(
          Field.Tensor("out2", DataType.DT_DOUBLE, TensorShape.vector(3))
        )
      )
      assert(ModelSignatureOps.append(sig1, sig2).isLeft)
    }
  }
}
