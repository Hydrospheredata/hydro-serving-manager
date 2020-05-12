package io.hydrosphere.serving.manager.domain.tensor.tensor_builder

import io.circe.Json
import io.hydrosphere.serving.manager.domain.contract.{Field, Signature, TensorShape}
import io.hydrosphere.serving.manager.domain.tensor.ValidationError.SignatureValidationError
import io.hydrosphere.serving.manager.domain.tensor.{TypedTensor, ValidationError}

class SignatureBuilder(val signature: Signature) {
  def convert(data: Json): Either[ValidationError, Map[String, TypedTensor[_]]] = {
    // rootField is a virtual field that aggregates all request inputs
    val rootField = Field.Map(
      name = "root",
      subfields = signature.inputs.toList,
      shape = TensorShape.Dynamic
    )

    val fieldValidator = new ComplexFieldBuilder(rootField)
    fieldValidator.convert(data) match {
      case Left(errors) =>
        Left(SignatureValidationError(errors, signature))
      case Right(tensor) =>
        Right(tensor.data.head)
    }
  }
}
