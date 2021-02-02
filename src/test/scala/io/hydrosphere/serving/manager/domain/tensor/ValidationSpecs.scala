package io.hydrosphere.serving.manager.domain.tensor

import cats.data.NonEmptyList
import com.google.protobuf.ByteString
import io.circe.syntax._
import io.hydrosphere.serving.manager.domain.contract.{Field, Signature, TensorShape}
import io.hydrosphere.serving.proto.contract.errors.ValidationError
import io.hydrosphere.serving.proto.contract.signature.{ModelSignature, SignatureBuilder}
import io.hydrosphere.serving.proto.contract.tensor.builders.SignatureBuilder
import io.hydrosphere.serving.proto.contract.tensor.definitions.{Shape, TypedTensor}
import io.hydrosphere.serving.proto.contract.types.DataType.{DT_BOOL, DT_FLOAT, DT_INT16, DT_STRING}
import org.scalatest.wordspec.AnyWordSpec

import io.circe.parser._

class ValidationSpecs extends AnyWordSpec {
  classOf[SignatureBuilder].getSimpleName should {
    "convert" when {
      "flat json is compatible with contract" in {
        val rawJson   = """
{
 "age": 2,
 "name": "Vasya",
 "isEmployed": true,
 "features": [1.0, 0.0, 0.5, 0.1]
}
          """
        val inputJson = parse(rawJson)

        val signature = ModelSignature(
          signatureName = "test",
          inputs = Seq(
            SignatureBuilder.simpleTensorModelField("name", DT_STRING, Shape.scalar),
            SignatureBuilder.simpleTensorModelField("age", DT_INT16, Shape.scalar),
            SignatureBuilder.simpleTensorModelField("isEmployed", DT_BOOL, Shape.scalar),
            SignatureBuilder.simpleTensorModelField("features", DT_FLOAT, Shape.vector(4))
          )
        )

        val validator = new SignatureBuilder(signature)

        val result = inputJson
          .flatMap(validator.convert)
          .map(_.view.mapValues(_.toProto))
          .right
          .get
          .toMap

        assert(result("age").intVal === Seq(2))
        assert(result("name").stringVal === Seq(ByteString.copyFromUtf8("Vasya")))
        assert(result("isEmployed").boolVal === Seq(true))
        assert(result("features").floatVal === Seq(1f, 0f, .5f, .1f))
      }

//      "nested json is compatible with contract" in {
//        val input =
//          """
//            |{
//            | "isOk": true,
//            | "person": {
//            |   "name": "Vasya",
//            |   "age": 18,
//            |   "isEmployed": true,
//            |   "features": [1.0, 0.0, 0.5, 0.1]
//            | }
//            |}
//          """.stripMargin.asJson
//
//        val signature = ModelSignature(
//          signatureName = "test",
//          inputs = Seq(
//            SignatureBuilder.simpleTensorModelField("isOk", DT_BOOL, Shape.scalar),
//            SignatureBuilder.complexField(
//              "person",
//              None,
//              Seq(
//                SignatureBuilder.simpleTensorModelField("name", DT_STRING, Shape.scalar),
//                SignatureBuilder.simpleTensorModelField("age", DT_INT16, Shape.scalar),
//                SignatureBuilder.simpleTensorModelField("isEmployed", DT_BOOL, Shape.scalar),
//                SignatureBuilder.simpleTensorModelField("features", DT_FLOAT, Shape.scalar)
//              )
//            )
//          )
//        )
//
//        val validator = new SignatureBuilder(signature)
//        val result    = validator.convert(input).right.get.mapValues(_.toProto)
//
//        assert(result("isOk").boolVal === Seq(true))
//
//        val person = result("person").mapVal.head.subtensors
//        assert(person("age").intVal === Seq(18))
//        assert(person("name").stringVal === Seq(ByteString.copyFromUtf8("Vasya")))
//        assert(person("isEmployed").boolVal === Seq(true))
//        assert(person("features").floatVal === Seq(1f, 0f, .5f, .1f))
//      }
    }

    "fail" when {
      "flat json is incompatible with contract" in {
        val input =
          """
            |{
            | "age": 2,
            | "name": "Vasya",
            | "isEmployed": true
            |}
          """.stripMargin.asJson

        val signature = ModelSignature(
          signatureName = "test",
          inputs = Seq(
            SignatureBuilder.simpleTensorModelField("name", DT_STRING, Shape.scalar),
            SignatureBuilder.simpleTensorModelField("birthday", DT_STRING, Shape.scalar)
          )
        )

        val validator = new SignatureBuilder(signature)
        val result    = validator.convert(input)

        assert(result.isLeft, result)
      }

//      "nested json is incompatible with contract" in {
//        val input =
//          """
//            |{
//            | "isOk": true,
//            | "person": {
//            |   "name": "Vasya",
//            |   "age": 18,
//            |   "isEmployed": true,
//            |   "features": [1.0, 0.0, 0.5, 0.1]
//            | }
//            |}
//          """.stripMargin.asJson
//
//        val signature = ModelSignature(
//          signatureName = "test",
//          inputs = Seq(
//            SignatureBuilder.simpleTensorModelField("isOk", DT_BOOL, Shape.scalar),
//            SignatureBuilder.complexField(
//              "person",
//              None,
//              Seq(
//                SignatureBuilder.simpleTensorModelField("surname", DT_STRING, Shape.scalar),
//                SignatureBuilder.simpleTensorModelField("age", DT_INT16, Shape.scalar),
//                SignatureBuilder.simpleTensorModelField("isEmployed", DT_BOOL, Shape.scalar),
//                SignatureBuilder.simpleTensorModelField("features", DT_FLOAT, Shape.vector(4))
//              )
//            )
//          )
//        )
//
//        val validator = new SignatureBuilder(signature)
//        val result    = validator.convert(input)
//
//        assert(result.isLeft, result)
//      }
    }
  }
}
