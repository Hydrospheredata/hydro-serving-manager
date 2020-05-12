package io.hydrosphere.serving.manager.domain.tensor

import cats.data.NonEmptyList
import com.google.protobuf.ByteString
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.contract.{DataType, Field, Signature, TensorShape}
import io.hydrosphere.serving.manager.util.JsonOps._
import io.hydrosphere.serving.manager.domain.tensor.tensor_builder.SignatureBuilder

class ValidationSpecs extends GenericUnitTest {
  describe("Json to tensor") {
    it("should convert when flat json is compatible with contract") {
      val input =
        """
          |{
          | "age": 2,
          | "name": "Vasya",
          | "isEmployed": true,
          | "features": [1.0, 0.0, 0.5, 0.1]
          |}
          """.stripMargin.parse.getOrElse(fail())

      val signature = Signature(
        signatureName = "test",
        inputs = NonEmptyList.of(
          Field.Tensor("name", DataType.DT_STRING, TensorShape.scalar),
          Field.Tensor("age", DataType.DT_INT16, TensorShape.scalar),
          Field.Tensor("isEmployed", DataType.DT_BOOL, TensorShape.scalar),
          Field.Tensor("features", DataType.DT_FLOAT, TensorShape.vector(4))
        ),
        outputs = NonEmptyList.of(
          Field.Tensor("features", DataType.DT_FLOAT, TensorShape.vector(4))
        )
      )

      val validator = new SignatureBuilder(signature)
      val result    = validator.convert(input).getOrElse(fail()).mapValues(_.toProto).toMap

      assert(result("age").intVal === Seq(2))
      assert(result("name").stringVal === Seq(ByteString.copyFromUtf8("Vasya")))
      assert(result("isEmployed").boolVal === Seq(true))
      assert(result("features").floatVal === Seq(1f, 0f, .5f, .1f))
    }

    it("should convert nested json is compatible with contract") {
      val input =
        """
          |{
          | "isOk": true,
          | "person": {
          |   "name": "Vasya",
          |   "age": 18,
          |   "isEmployed": true,
          |   "features": [1.0, 0.0, 0.5, 0.1]
          | }
          |}
          """.stripMargin.parse.getOrElse(fail())

      val signature = Signature(
        signatureName = "test",
        inputs = NonEmptyList.of(
          Field.Tensor("isOk", DataType.DT_BOOL, TensorShape.scalar),
          Field.Map(
            "person",
            List(
              Field.Tensor("name", DataType.DT_STRING, TensorShape.scalar),
              Field.Tensor("age", DataType.DT_INT16, TensorShape.scalar),
              Field.Tensor("isEmployed", DataType.DT_BOOL, TensorShape.scalar),
              Field.Tensor("features", DataType.DT_FLOAT, TensorShape.vector(4))
            ),
            TensorShape.Dynamic
          )
        ),
        outputs = NonEmptyList.of(
          Field.Tensor("isOk", DataType.DT_BOOL, TensorShape.scalar)
        )
      )

      val validator = new SignatureBuilder(signature)
      val result    = validator.convert(input).getOrElse(fail()).mapValues(_.toProto).toMap

      assert(result("isOk").boolVal === Seq(true))

      val person = result("person").mapVal.head.subtensors
      assert(person("age").intVal === Seq(18))
      assert(person("name").stringVal === Seq(ByteString.copyFromUtf8("Vasya")))
      assert(person("isEmployed").boolVal === Seq(true))
      assert(person("features").floatVal === Seq(1f, 0f, .5f, .1f))
    }

    it("should fail when flat json is incompatible with contract") {
      val input =
        """
          |{
          | "age": 2,
          | "name": "Vasya",
          | "isEmployed": true
          |}
          """.stripMargin.parse.getOrElse(fail())

      val signature = Signature(
        signatureName = "test",
        inputs = NonEmptyList.of(
          Field.Tensor("name", DataType.DT_STRING, TensorShape.scalar),
          Field.Tensor("birthday", DataType.DT_STRING, TensorShape.scalar)
        ),
        outputs = NonEmptyList.of(
          Field.Tensor("name", DataType.DT_STRING, TensorShape.scalar)
        )
      )

      val validator = new SignatureBuilder(signature)
      val result    = validator.convert(input)

      println(result)
      assert(result.isLeft, result)
    }

    it("should fail when nested json is incompatible with contract") {
      val input =
        """
          |{
          | "isOk": true,
          | "person": {
          |   "name": "Vasya",
          |   "age": 18,
          |   "isEmployed": true,
          |   "features": [1.0, 0.0, 0.5, 0.1]
          | }
          |}
          """.stripMargin.parse.getOrElse(fail())

      val signature = Signature(
        signatureName = "test",
        inputs = NonEmptyList.of(
          Field.Tensor("isOk", DataType.DT_BOOL, TensorShape.scalar),
          Field.Map(
            "person",
            List(
              Field.Tensor("surname", DataType.DT_STRING, TensorShape.scalar),
              Field.Tensor("age", DataType.DT_INT16, TensorShape.scalar),
              Field.Tensor("isEmployed", DataType.DT_BOOL, TensorShape.scalar),
              Field.Tensor("features", DataType.DT_FLOAT, TensorShape.vector(4))
            ),
            TensorShape.Dynamic
          )
        ),
        outputs = NonEmptyList.of(
          Field.Tensor("isOk", DataType.DT_BOOL, TensorShape.scalar)
        )
      )

      val validator = new SignatureBuilder(signature)
      val result    = validator.convert(input)

      println(result)
      assert(result.isLeft, result)
    }
  }
}
