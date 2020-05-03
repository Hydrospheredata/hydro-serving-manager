package io.hydrosphere.serving.manager.domain.contract

import enumeratum.{CirceEnum, Enum, EnumEntry}
import io.hydrosphere.serving.tensorflow.types.{DataType => ProtoDataType}

sealed trait DataType extends EnumEntry with Product

case object DataType extends Enum[DataType] with CirceEnum[DataType] {

  case object DT_FLOAT extends DataType

  case object DT_DOUBLE extends DataType

  case object DT_INT32 extends DataType

  case object DT_UINT8 extends DataType

  case object DT_INT16 extends DataType

  case object DT_INT8 extends DataType

  case object DT_STRING extends DataType

  case object DT_COMPLEX64 extends DataType

  case object DT_INT64 extends DataType

  case object DT_BOOL extends DataType

  case object DT_QINT8 extends DataType

  case object DT_QUINT8 extends DataType

  case object DT_QINT32 extends DataType

  case object DT_BFLOAT16 extends DataType

  case object DT_QINT16 extends DataType

  case object DT_QUINT16 extends DataType

  case object DT_UINT16 extends DataType

  case object DT_COMPLEX128 extends DataType

  case object DT_HALF extends DataType

  case object DT_RESOURCE extends DataType

  case object DT_VARIANT extends DataType

  case object DT_UINT32 extends DataType

  case object DT_UINT64 extends DataType

  override def values: IndexedSeq[DataType] = findValues

  def toProto(dtype: DataType): ProtoDataType = {
    ProtoDataType.fromName(dtype.entryName).getOrElse(ProtoDataType.DT_INVALID)
  }

  def fromProto(protoDtype: ProtoDataType): Option[DataType] = {
    DataType.withNameInsensitiveOption(protoDtype.name)
  }

}
