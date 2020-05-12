package io.hydrosphere.serving.manager.domain.tensor

import io.hydrosphere.serving.manager.domain.contract._

sealed trait ValidationError extends Throwable

object ValidationError {

  final case class SignatureMissingError(expectedSignature: String, modelContract: Contract)
      extends ValidationError

  final case class SignatureValidationError(
      suberrors: Seq[ValidationError],
      modelSignature: Signature
  ) extends ValidationError

  final case class FieldMissingError(expectedField: String) extends ValidationError

  final case class ComplexFieldValidationError(suberrors: Seq[ValidationError], field: Field.Map)
      extends ValidationError

  final case class IncompatibleFieldTypeError(field: String, expectedType: DataType)
      extends ValidationError

  final case class UnsupportedFieldTypeError(expectedType: DataType) extends ValidationError

  final case class IncompatibleFieldShapeError(
      field: String,
      expectedShape: TensorShape
  ) extends ValidationError

  final case class InvalidFieldData[T](actualClass: Class[T]) extends ValidationError

}
