package io.hydrosphere.serving.manager.domain.application

import cats.data.NonEmptyList
import cats.implicits._
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.contract.ops.{MergeError, ModelSignatureOps}

object ApplicationValidator {

  /**
    * Check if name matches with  `[a-zA-Z\-_\d]+` regexp
    *
    * @param name application name
    * @return
    */
  def name(name: String): Option[String] = {
    val validName = """^[a-zA-Z\-_\d]+$$""".r
    if (validName.pattern.matcher(name).matches())
      Some(name)
    else
      None
  }

  /**
    * Checks if different ModelVariants are mergeable into single stage.
    *
    * @param modelVariants modelVariants
    * @return
    */
  def inferStageSignature(
      modelVariants: NonEmptyList[ModelVersion.Internal]
  ): Either[DomainError, Signature] = {
    val signatures    = modelVariants.map(_.modelContract.predict)
    val signatureName = signatures.head.signatureName
    val isSameName    = signatures.forall(_.signatureName == signatureName)
    if (isSameName) {
      val res =
        signatures.tail.foldRight(
          Either.right[NonEmptyList[MergeError], Signature](signatures.head)
        ) {
          case (acc, Right(sig)) =>
            ModelSignatureOps.merge(sig, acc)
          case (_, x) => x
        }
      res
        .bimap(
          error => DomainError.invalidRequest(s"Errors while merging signatures: $error"),
          res => res.copy(signatureName = signatureName)
        )
    } else
      Left(
        DomainError.invalidRequest(
          s"Model Versions ${modelVariants.map(_.fullName)} have different signature names"
        )
      )
  }
}
