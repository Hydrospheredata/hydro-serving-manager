package io.hydrosphere.serving.manager.domain.model_version

import enumeratum.{CirceEnum, Enum, EnumEntry}

sealed trait ModelVersionStatus extends EnumEntry

case object ModelVersionStatus extends Enum[ModelVersionStatus] with CirceEnum[ModelVersionStatus] {

  final case object Assembling extends ModelVersionStatus

  final case object Released extends ModelVersionStatus

  final case object Failed extends ModelVersionStatus

  override def values: IndexedSeq[ModelVersionStatus] = findValues
}
