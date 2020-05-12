package io.hydrosphere.serving.manager.util

import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.contract.TensorShape
import io.hydrosphere.serving.manager.domain.tensor.DoubleTensor
import io.hydrosphere.serving.manager.domain.tensor.json.TensorJsonLens

import io.circe.syntax._

class JsonConverterSpec extends GenericUnitTest {
  describe("Json Tensor converters") {
    it("should convert tensor without dims") {
      val t1 = DoubleTensor(TensorShape.Dynamic, Seq(1, 2, 3, 4))
      val t2 = DoubleTensor(TensorShape.Dynamic, Seq(1))

      val res1 = TensorJsonLens.toJson(t1)
      val res2 = TensorJsonLens.toJson(t2)
      assert(res1 === List(1, 2, 3, 4).asJson)
      assert(res2 === List(1).asJson)
    }

    it("should convert scalar tensor") {
      val t1 = DoubleTensor(TensorShape.scalar, Seq(1))

      val res1 = TensorJsonLens.toJson(t1)
      assert(res1 === 1.asJson)
    }
    it("should convert tensor with dims") {
      val t1 = DoubleTensor(TensorShape.varVector, Seq(1, 2, 3, 4))

      val res1 = TensorJsonLens.toJson(t1)
      assert(res1 === List(1, 2, 3, 4).asJson)
    }
  }
}
