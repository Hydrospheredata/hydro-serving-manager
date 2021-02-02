package io.hydrosphere.serving.manager.domain.contract.ops

import cats.data.NonEmptyList
import io.hydrosphere.serving.manager.domain.contract.Signature

trait ModelSignatureOps {
  def merge(
      signature1: Signature,
      signature2: Signature
  ): Either[NonEmptyList[MergeError], Signature] =
    for {
      mergedIns  <- ModelFieldOps.mergeAll(signature1.inputs, signature2.inputs)
      mergedOuts <- ModelFieldOps.mergeAll(signature1.outputs, signature2.outputs)
    } yield Signature(
      s"${signature1.signatureName}&${signature2.signatureName}",
      mergedIns,
      mergedOuts
    )

  def append(head: Signature, tail: Signature): Either[NonEmptyList[MergeError], Signature] =
    ModelFieldOps
      .appendAll(head.outputs, tail.inputs)
      .map { _ =>
        Signature(
          s"${head.signatureName}>${tail.signatureName}",
          head.inputs,
          tail.outputs
        )
      }
}

object ModelSignatureOps extends ModelSignatureOps
