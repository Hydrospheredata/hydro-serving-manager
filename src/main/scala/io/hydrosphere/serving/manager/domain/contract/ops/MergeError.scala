package io.hydrosphere.serving.manager.domain.contract.ops

import io.hydrosphere.serving.manager.domain.contract.Field

sealed trait MergeError extends Throwable with Serializable with Product

object MergeError {
  case class FieldNotFound(fieldName: String) extends MergeError

  def fieldNotFound(fieldName: String): MergeError = FieldNotFound(fieldName)

  case class NamesAreDifferent(m1: Field, m2: Field) extends MergeError

  def namesAreDifferent(m1: Field, m2: Field): MergeError = NamesAreDifferent(m1, m2)

  case class IncompatibleTypes(m1: Field, m2: Field) extends MergeError

  def incompatibleTypes(m1: Field, m2: Field): MergeError = IncompatibleTypes(m1, m2)

  case class IncompatibleShapes(m1: Field, m2: Field) extends MergeError

  def incompatibleShapes(m1: Field, m2: Field): MergeError = IncompatibleShapes(m1, m2)
}
