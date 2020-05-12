package io.hydrosphere.serving.manager.domain.tensor

import io.hydrosphere.serving.manager.GenericUnitTest
import io.circe.syntax._
import io.hydrosphere.serving.manager.domain.contract.TensorShape
import io.hydrosphere.serving.manager.domain.tensor.json.TensorJsonLens
import io.hydrosphere.serving.manager.util.JsonOps._

class TensorJson extends GenericUnitTest {
  describe("TensorJsonLens") {
    it("should convert matrix[1, 2, 1, 2]") {
      val stensor = StringTensor(TensorShape.mat(1, 2, 1, 2), Seq("never", "gonna", "give", "you"))

      val expected = List(
        List(
          List(
            List("never", "gonna")
          ),
          List(
            List("give", "you")
          )
        )
      ).asJson

      assert(TensorJsonLens.toJson(stensor) === expected)
    }

    it("should convert matrix[2, 1, 1, 1, 1]") {
      val stensor = StringTensor(TensorShape.mat(2, 1, 1, 1, 1), Seq("never", "gonna"))

      val expected = List(
        List(List(List(List("never")))),
        List(List(List(List("gonna"))))
      ).asJson

      assert(TensorJsonLens.toJson(stensor) === expected)
    }

    it("should convert matrix[2,3]") {
      val stensor =
        StringTensor(TensorShape.mat(2, 3), Seq("never", "gonna", "give", "you", "up", "and"))

      val expected = List(
        List("never", "gonna", "give"),
        List("you", "up", "and")
      ).asJson

      assert(TensorJsonLens.toJson(stensor) === expected)
    }

    it("should convert matrix[1,4]") {
      val stensor = StringTensor(TensorShape.mat(1, 4), Seq("never", "gonna", "give", "you"))

      val expected = List(
        List("never", "gonna", "give", "you")
      ).asJson

      assert(TensorJsonLens.toJson(stensor) === expected)
    }

    it("should convert matrix[-1,2]") {
      val stensor = StringTensor(TensorShape.mat(-1, 2), Seq("never", "gonna", "give", "you"))

      val expected = List(
        List("never", "gonna"),
        List("give", "you")
      ).asJson

      assert(TensorJsonLens.toJson(stensor) === expected)
    }

    it("should convert matrix[2,2]") {
      val stensor = StringTensor(TensorShape.mat(2, 2), Seq("never", "gonna", "give", "you"))

      val expected = List(
        List("never", "gonna"),
        List("give", "you")
      ).asJson

      assert(TensorJsonLens.toJson(stensor) === expected)
    }

    it("should convert vector[-1]") {
      val stensor = StringTensor(TensorShape.varVector, Seq("never", "gonna", "give", "you"))

      val expected = List(
        "never",
        "gonna",
        "give",
        "you"
      ).asJson

      assert(TensorJsonLens.toJson(stensor) === expected)
    }

    it("should convert scalar") {
      val stensor  = StringTensor(TensorShape.scalar, Seq("never"))
      val expected = "never".asJson
      assert(TensorJsonLens.toJson(stensor) === expected)
    }

    it("should convert maps") {
      val stensor = MapTensor(
        shape = TensorShape.scalar,
        data = Seq(
          Map(
            "name" -> StringTensor(
              TensorShape.scalar,
              Seq("Rick")
            ),
            "email" -> StringTensor(
              TensorShape.scalar,
              Seq("rick@roll.com")
            ),
            "age" -> Int32Tensor(
              TensorShape.scalar,
              Seq(32)
            )
          )
        )
      )

      val expected =
        """
          |{
          | "name": "Rick",
          | "email": "rick@roll.com",
          | "age": 32
          | }
          |""".stripMargin.parse.getOrElse(throw new Exception("OOOPS"))

      assert(TensorJsonLens.toJson(stensor) === expected)
    }
  }
}
