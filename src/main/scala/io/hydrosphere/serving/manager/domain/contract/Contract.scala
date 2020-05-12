package io.hydrosphere.serving.manager.domain.contract

import cats.data.Validated.Invalid
import cats.data._
import cats.implicits._
import io.circe.generic.JsonCodec
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.DomainError.InvalidRequest

@JsonCodec
final case class Signature(
    signatureName: String,
    inputs: NonEmptyList[Field],
    outputs: NonEmptyList[Field]
)

object Signature {
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
      inputs = signature.inputs.toList.map(Field.toProto)
    )

  def fromProto(signature: ModelSignature): Either[Throwable, Signature] = {
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

@JsonCodec
final case class Contract(predict: Signature)

object Contract {
  def validateContract(contract: Contract): ValidatedNec[InvalidRequest, Contract] =
    (
      Signature.validateName(contract.predict.signatureName),
      Signature.validateInputs(contract.predict),
      Signature.validateOutputs(contract.predict)
    ).mapN((_, _, _) => contract)

  def fromProto(mc: ModelContract): Either[Throwable, Contract] =
    mc.predict match {
      case Some(value) => Signature.fromProto(value).map(Contract.apply)
      case None        => Left(DomainError.invalidRequest("predict signature is required"))
    }

  def toProto(mc: Contract): ModelContract =
    ModelContract(
      predict = Signature.toProto(mc.predict).some
    )
}
