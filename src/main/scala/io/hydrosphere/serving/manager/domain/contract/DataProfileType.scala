package io.hydrosphere.serving.manager.domain.contract

import enumeratum._
import io.hydrosphere.serving.proto.contract.types.{DataProfileType => ProtoDataProfileType}

sealed trait DataProfileType extends EnumEntry

case object DataProfileType extends Enum[DataProfileType] with CirceEnum[DataProfileType] {

  case object CATEGORICAL extends DataProfileType

  case object NOMINAL extends DataProfileType

  case object ORDINAL extends DataProfileType

  case object NUMERICAL extends DataProfileType

  case object CONTINUOUS extends DataProfileType

  case object INTERVAL extends DataProfileType

  case object RATIO extends DataProfileType

  case object IMAGE extends DataProfileType

  case object VIDEO extends DataProfileType

  case object AUDIO extends DataProfileType

  case object TEXT extends DataProfileType

  override def values: IndexedSeq[DataProfileType] = findValues

  def toProto(profileType: DataProfileType): ProtoDataProfileType =
    ProtoDataProfileType.fromName(profileType.entryName).getOrElse(ProtoDataProfileType.NONE)

  def fromProto(protoProfileType: ProtoDataProfileType): Either[String, Option[DataProfileType]] =
    protoProfileType match {
      case ProtoDataProfileType.NONE => Right(None)
      case x =>
        DataProfileType
          .withNameInsensitiveOption(x.name)
          .toRight(s"Unknown DataProfile enum: ${protoProfileType.name}")
          .map(Some.apply)
    }

  val protoNone: ProtoDataProfileType = ProtoDataProfileType.NONE
}
