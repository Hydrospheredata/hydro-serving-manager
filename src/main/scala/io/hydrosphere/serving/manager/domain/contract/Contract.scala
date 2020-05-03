package io.hydrosphere.serving.manager.domain.contract

import cats.data.Validated.Invalid
import cats.data._
import cats.implicits._
import io.circe.generic.JsonCodec
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.contract.model_field.ModelField
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.domain.DomainError.InvalidRequest

@JsonCodec
final case class Signature(
    signatureName: String,
    inputs: NonEmptyList[Field],
    outputs: NonEmptyList[Field]
)

@JsonCodec
final case class Contract(predict: Signature)

object Contract {
  def validateContract(contract: ModelContract): ValidatedNec[InvalidRequest, ModelContract] = {
    contract.predict match {
      case None => Validated.invalidNec((InvalidRequest("The model has no prediction signature")))
      case Some(predictSignature) =>
        (
          validateName(predictSignature),
          validateInputs(predictSignature),
          validateOutputs(predictSignature)
        ).mapN((_, _, _) => contract)
    }
  }

  def validateName(signature: ModelSignature): ValidatedNec[InvalidRequest, Unit] = {
    Validated.condNec(
      signature.signatureName.trim.nonEmpty,
      (),
      InvalidRequest("Signature name is empty")
    )
  }

  def validateInputs(signature: ModelSignature): ValidatedNec[InvalidRequest, Unit] = {
    signature.inputs match {
      case Nil =>
        Validated.invalidNec(
          InvalidRequest(s"Signature ${signature.signatureName} has empty inputs")
        )
      case x => x.toList.traverse(validateField).as(())
    }
  }

  def validateOutputs(signature: ModelSignature): ValidatedNec[InvalidRequest, Unit] = {
    signature.outputs match {
      case Nil =>
        Validated.invalidNec(
          InvalidRequest(s"Signature ${signature.signatureName} has empty outputs")
        )
      case x => x.toList.traverse(validateField).as(())
    }
  }

  def validateField(modelField: ModelField): ValidatedNec[InvalidRequest, ModelField] = {
    modelField.typeOrSubfields match {
      case ModelField.TypeOrSubfields.Dtype(dtype) if dtype.isDtInvalid || dtype.isUnrecognized =>
        Validated.invalidNec(InvalidRequest(s"${modelField.name}: Invalid Dtype $dtype"))

      case ModelField.TypeOrSubfields.Dtype(_) =>
        Validated.validNec(modelField)

      case ModelField.TypeOrSubfields.Subfields(subfields) =>
        val errors = subfields.data
          .map(validateField)
          .collect { case Invalid(e) => e }
          .foldLeft(Chain.empty[InvalidRequest]) { case (a, b) => a ++ b.toChain }
        NonEmptyChain.fromChain(errors) match {
          case Some(value) => Validated.invalid(value)
          case None        => Validated.valid(modelField)
        }

      case ModelField.TypeOrSubfields.Empty =>
        Validated.invalidNec(InvalidRequest(s"${modelField.name}: Type cannot be empty."))
    }
  }
  def fromProto(mc: ModelContract): Either[Throwable, Contract] = ???
  def toContract(mc: Contract): ModelContract                   = ???
}
