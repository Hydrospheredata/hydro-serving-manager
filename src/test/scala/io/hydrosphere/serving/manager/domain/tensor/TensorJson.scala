package io.hydrosphere.serving.manager.domain.tensor

import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.proto.contract.tensor.conversions.json.TensorJsonLens
import io.hydrosphere.serving.proto.contract.tensor.definitions.{
  Int32Tensor,
  MapTensor,
  Shape,
  StringTensor
}
import io.circe.generic.auto._, io.circe.syntax._

class TensorJson extends GenericUnitTest {

  describe("TensorJsonLens") {
    it("should convert matrix[1, 2, 1, 2]") {
      val stensor = StringTensor(Shape.mat(1, 2, 1, 2), Seq("never", "gonna", "give", "you"))

      val expected = Array(Array(Array(Array("never", "gonna")), Array(Array("give", "you"))))

      assert(TensorJsonLens.toJson(stensor) === expected.asJson)
    }

    it("should convert matrix[2, 1, 1, 1, 1]") {
      val stensor = StringTensor(Shape.mat(2, 1, 1, 1, 1), Seq("never", "gonna"))

      val expected = Array(
        Array(Array(Array(Array("never")))),
        Array(Array(Array(Array("gonna"))))
      ).asJson

      assert(TensorJsonLens.toJson(stensor) === expected)
    }

    it("should convert matrix[2,3]") {
      val stensor =
        StringTensor(Shape.mat(2, 3), Seq("never", "gonna", "give", "you", "up", "and"))

      val expected = Array(
        Array("never", "gonna", "give"),
        Array("you", "up", "and")
      ).asJson

      assert(TensorJsonLens.toJson(stensor) === expected)
    }

    it("should convert matrix[1,4]") {
      val stensor = StringTensor(Shape.mat(1, 4), Seq("never", "gonna", "give", "you"))

      val expected = Array(
        Array("never", "gonna", "give", "you")
      ).asJson

      assert(TensorJsonLens.toJson(stensor) === expected)
    }

    it("should convert matrix[-1,2]") {
      val stensor = StringTensor(Shape.mat(-1, 2), Seq("never", "gonna", "give", "you"))

      val expected = Array(
        Array("never", "gonna"),
        Array("give", "you")
      ).asJson

      assert(TensorJsonLens.toJson(stensor) === expected)
    }

    it("should convert matrix[2,2]") {
      val stensor = StringTensor(Shape.mat(2, 2), Seq("never", "gonna", "give", "you"))

      val expected = Array(
        Array("never", "gonna"),
        Array("give", "you")
      ).asJson

      assert(TensorJsonLens.toJson(stensor) === expected)
    }

    it("should convert vector[-1]") {
      val stensor = StringTensor(Shape.vector(-1), Seq("never", "gonna", "give", "you"))

      val expected = Array(
        "never",
        "gonna",
        "give",
        "you"
      ).asJson

      assert(TensorJsonLens.toJson(stensor) === expected)
    }

    it("should convert scalar") {
      val stensor  = StringTensor(Shape.scalar, Seq("never"))
      val expected = "never".asJson
      assert(TensorJsonLens.toJson(stensor) === expected)
    }

    it("should convert maps") {
      val stensor = MapTensor(
        shape = Shape.scalar,
        data = Seq(
          Map(
            "name" -> StringTensor(
              Shape.scalar,
              Seq("Rick")
            ),
            "email" -> StringTensor(
              Shape.scalar,
              Seq("rick@roll.com")
            ),
            "age" -> Int32Tensor(
              Shape.scalar,
              Seq(32)
            )
          )
        )
      )

      //TODO: Map[String, Any]
      val expected =
        Map[String, String](
          "name"  -> "Rick",
          "email" -> "rick@roll.com",
          "age"   -> "32"
        ).asJson

      assert(TensorJsonLens.toJson(stensor) === expected)
    }
  }
}
