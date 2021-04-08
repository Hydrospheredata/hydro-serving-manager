package io.hydrosphere.serving.manager.domain.contract

import cats.Eq
import cats.data.Validated.Invalid
import cats.data._
import cats.implicits._
import io.circe.generic.JsonCodec
import io.hydrosphere.serving.proto.contract.signature.ModelSignature
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.DomainError.InvalidRequest
import io.hydrosphere.serving.manager.domain.contract.DataType.DT_INT64
import io.hydrosphere.serving.manager.infrastructure.protocol.FieldDerivation._

@JsonCodec
final case class Signature(
    signatureName: String,
    inputs: NonEmptyList[Field],
    outputs: NonEmptyList[Field]
)

object Signature {
  lazy val defaultSignature: Signature = Signature(
    "default_signature",
    NonEmptyList.of(Field.Tensor("input", DT_INT64, TensorShape.scalar)),
    NonEmptyList.of(Field.Tensor("output", DT_INT64, TensorShape.scalar))
  )

  def validate(signature: Signature): ValidatedNec[InvalidRequest, Signature] =
    (
      Signature.validateName(signature.signatureName),
      Signature.validateInputs(signature),
      Signature.validateOutputs(signature)
    ).mapN((_, _, _) => signature)

  def validateName(signatureName: String): ValidatedNec[InvalidRequest, Unit] =
    Validated.condNec(
      signatureName.trim.nonEmpty,
      (),
      InvalidRequest("Signature name is empty")
    )

  def validateInputs(signature: Signature): ValidatedNec[InvalidRequest, Unit] =
    signature.inputs.traverse(validateField).as(())

  def validateOutputs(signature: Signature): ValidatedNec[InvalidRequest, Unit] =
    signature.outputs.traverse(validateField).as(())

  def validateField(modelField: Field): ValidatedNec[InvalidRequest, Field] =
    modelField match {
      case Field.Tensor(_, _, _, _) => Validated.validNec(modelField)
      case Field.Map(_, subfields, _) =>
        val errors = subfields
          .map(validateField)
          .collect { case Invalid(e) => e }
          .foldLeft(Chain.empty[InvalidRequest]) { case (a, b) => a ++ b.toChain }
        NonEmptyChain.fromChain(errors) match {
          case Some(value) => Validated.invalid(value)
          case None        => Validated.valid(modelField)
        }
    }

  def toProto(signature: Signature): ModelSignature =
    ModelSignature(
      signatureName = signature.signatureName,
      inputs = signature.inputs.toList.map(Field.toProto),
      outputs = signature.outputs.toList.map(Field.toProto)
    )

  def fromProto(signature: ModelSignature): Either[DomainError, Signature] = {
    val nameV = Signature
      .validateName(signature.signatureName)
      .as(signature.signatureName)
      .leftMap(_.map(_.message))

    val inputsV = NonEmptyList
      .fromList(signature.inputs.toList)
      .toRightNec("Signature inputs required")
      .flatMap(x => x.traverse(Field.fromProto))

    val outputsV = NonEmptyList
      .fromList(signature.outputs.toList)
      .toRightNec("Signature outputs required")
      .flatMap(x => x.traverse(Field.fromProto))
    (
      nameV,
      inputsV.toValidated,
      outputsV.toValidated
    ).mapN((name, inputs, outputs) => Signature(name, inputs, outputs))
      .leftMap { errors =>
        DomainError.invalidRequest(s"Invalid signature. Errors: ${errors.toList.mkString(",")}")
      }
      .toEither
  }

}
