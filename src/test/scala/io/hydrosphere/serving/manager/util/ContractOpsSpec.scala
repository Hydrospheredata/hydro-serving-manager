package io.hydrosphere.serving.manager.util

import cats.data.NonEmptyList
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.contract.ops.ModelSignatureOps
import io.hydrosphere.serving.manager.domain.contract.{DataType, Field, Signature, TensorShape}

class ContractOpsSpec extends GenericUnitTest {
  describe("ContractOps") {
    it("should merge when signatures don't overlap") {
      val sig1 = Signature(
        "sig1",
        NonEmptyList.of(
          Field.Tensor("in1", DataType.DT_STRING, TensorShape.scalar)
        ),
        NonEmptyList.of(
          Field.Tensor("out1", DataType.DT_DOUBLE, TensorShape.varVector)
        )
      )
      val sig2 = Signature(
        "sig2",
        NonEmptyList.of(
          Field.Tensor("in2", DataType.DT_INT32, TensorShape.scalar)
        ),
        NonEmptyList.of(
          Field.Tensor("out2", DataType.DT_INT32, TensorShape.vector(3))
        )
      )

      val expectedSig = Signature(
        "sig1&sig2",
        NonEmptyList.of(
          Field.Tensor("in1", DataType.DT_STRING, TensorShape.scalar),
          Field.Tensor("in2", DataType.DT_INT32, TensorShape.scalar)
        ),
        NonEmptyList.of(
          Field.Tensor("out1", DataType.DT_DOUBLE, TensorShape.varVector),
          Field.Tensor("out2", DataType.DT_INT32, TensorShape.vector(3))
        )
      )
      val res = ModelSignatureOps.merge(sig1, sig2)
      assert(res.getOrElse(fail()) === expectedSig, res)
    }

    it("should merge when inputs are overlapping and compatible") {
      val sig1 = Signature(
        "sig1",
        NonEmptyList.of(
          Field.Tensor("in1", DataType.DT_INT32, TensorShape.varVector)
        ),
        NonEmptyList.of(
          Field.Tensor("out1", DataType.DT_DOUBLE, TensorShape.varVector)
        )
      )
      val sig2 = Signature(
        "sig2",
        NonEmptyList.of(
          Field.Tensor("in1", DataType.DT_INT32, TensorShape.vector(3))
        ),
        NonEmptyList.of(
          Field.Tensor("out2", DataType.DT_INT32, TensorShape.vector(3))
        )
      )

      val expectedSig = Signature(
        "sig1&sig2",
        NonEmptyList.of(
          Field.Tensor("in1", DataType.DT_INT32, TensorShape.vector(3))
        ),
        NonEmptyList.of(
          Field.Tensor("out1", DataType.DT_DOUBLE, TensorShape.varVector),
          Field.Tensor("out2", DataType.DT_INT32, TensorShape.vector(3))
        )
      )
      val res = ModelSignatureOps.merge(sig1, sig2)
      assert(res.isRight, res)
      assert(res.getOrElse(fail()) === expectedSig, res)
    }
  }

  it("shouldn't merge inputs overlap is conflicting") {
    val sig1 = Signature(
      "sig1",
      NonEmptyList.of(
        Field.Tensor("in1", DataType.DT_STRING, TensorShape.scalar)
      ),
      NonEmptyList.of(
        Field.Tensor("out1", DataType.DT_DOUBLE, TensorShape.varVector)
      )
    )
    val sig2 = Signature(
      "sig2",
      NonEmptyList.of(
        Field.Tensor("in1", DataType.DT_INT32, TensorShape.scalar)
      ),
      NonEmptyList.of(
        Field.Tensor("out2", DataType.DT_INT32, TensorShape.vector(3))
      )
    )

    assert(ModelSignatureOps.merge(sig1, sig2).isLeft)
  }

  it("shouldn't merge when outputs overlap is conflicting") {
    val sig1 = Signature(
      "sig1",
      NonEmptyList.of(
        Field.Tensor("in1", DataType.DT_STRING, TensorShape.scalar)
      ),
      NonEmptyList.of(
        Field.Tensor("out1", DataType.DT_DOUBLE, TensorShape.varVector)
      )
    )
    val sig2 = Signature(
      "sig2",
      NonEmptyList.of(
        Field.Tensor("in2", DataType.DT_INT32, TensorShape.scalar)
      ),
      NonEmptyList.of(
        Field.Tensor("out1", DataType.DT_INT32, TensorShape.vector(3))
      )
    )

    assert(ModelSignatureOps.merge(sig1, sig2).isLeft)
  }
}
