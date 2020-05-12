package io.hydrosphere.serving.manager.domain

import cats.data.NonEmptyList
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.contract._

class ContractSpec extends GenericUnitTest {
  describe("contract validation") {
    it("should fail if name is empty") {
      val contract = Contract(
        Signature(
          "",
          NonEmptyList.of(Field.Tensor("aaa", DataType.DT_FLOAT, TensorShape.Dynamic, None)),
          NonEmptyList.of(Field.Tensor("aaa", DataType.DT_FLOAT, TensorShape.Dynamic, None))
        )
      )
      val res = Contract.validateContract(contract)
      assert(res.isInvalid, res)
    }

    it("should pass if ok") {
      val contract = Contract(
        Signature(
          "okContract",
          NonEmptyList.of(Field.Tensor("aaa", DataType.DT_FLOAT, TensorShape.Dynamic, None)),
          NonEmptyList.of(Field.Tensor("aaa", DataType.DT_FLOAT, TensorShape.Dynamic, None))
        )
      )
      val res = Contract.validateContract(contract)
      assert(res.isValid, res)
    }
  }
}
