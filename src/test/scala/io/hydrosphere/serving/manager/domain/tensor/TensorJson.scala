package io.hydrosphere.serving.manager.domain.tensor

import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.proto.contract.tensor.conversions.json.TensorJsonLens
import io.hydrosphere.serving.proto.contract.tensor.definitions.{
  Int32Tensor,
  MapTensor,
  Shape,
  StringTensor
}
import io.circe.syntax._
import io.circe.parser._
import io.hydrosphere.serving.manager.domain.contract.DataType.DT_INT64
import io.hydrosphere.serving.manager.domain.contract.Field.Tensor
import io.hydrosphere.serving.manager.domain.contract.TensorShape

class TensorJson extends GenericUnitTest {

  describe("Json") {
    it("with shape equals null should be decoded as Tensor with Dynamic shape") {
      val rawJson = """{
                      |			"name": "input",
                      |			"profile": "NUMERICAL",
                      |			"shape": null,
                      |			"dtype": "DT_INT64"
                      |		}""".stripMargin

      val tensor = decode[Tensor](rawJson)

      assert(tensor.isRight)
      assert(tensor.right.get.shape == TensorShape.Dynamic)
    }

    it("with shape as object with dims field should be decoded as Tensor with Static shape") {
      val rawJson = """{
                      |			"name": "input",
                      |			"profile": "NUMERICAL",
                      |			"shape": {"dims": [1,2,3]},
                      |			"dtype": "DT_INT64"
                      |		}""".stripMargin

      val tensor = decode[Tensor](rawJson)

      assert(tensor.isRight)
      assert(tensor.right.get.shape == TensorShape.Static(List(1, 2, 3)))
    }

    it("with shape as object with empty dims array should be decoded as Tensor with Static shape") {
      val rawJson = """{
                      |			"name": "input",
                      |			"profile": "NUMERICAL",
                      |			"shape": {"dims": []},
                      |			"dtype": "DT_INT64"
                      |		}""".stripMargin

      val tensor = decode[Tensor](rawJson)

      assert(tensor.isRight)
      assert(tensor.right.get.shape == TensorShape.Static(List()))
    }
  }

  describe("Tensor") {
    it("with Dynamic shape should encoded correctly") {
      val tensor = Tensor("input", DT_INT64, TensorShape.Dynamic, None)
      val expectedRawJson =
        """{"dtype":"DT_INT64","name":"input","profile":null,"shape":null}""".stripMargin
      assert(tensor.asJson.noSpacesSortKeys.stripMargin === expectedRawJson)
    }
    it("with Static shape should encoded correctly") {
      val tensor = Tensor("input", DT_INT64, TensorShape.Static(List(1, 2, 3)), None)
      val expectedRawJson =
        """{"dtype":"DT_INT64","name":"input","profile":null,"shape":{"dims":[1,2,3]}}""".stripMargin
      assert(tensor.asJson.noSpacesSortKeys.stripMargin === expectedRawJson)
    }
  }

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
