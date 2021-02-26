package io.hydrosphere.serving.manager.domain.contract

import cats.Eq
import cats.data.EitherNec
import cats.implicits._
import io.circe.generic.JsonCodec
import io.hydrosphere.serving.proto.contract.field.ModelField
import io.hydrosphere.serving.proto.contract.field.ModelField.TypeOrSubfields
import io.hydrosphere.serving.manager.infrastructure.protocol.TensorShapeDerivation._
@JsonCodec
sealed trait Field extends Product with Serializable {
  def name: String
  def shape: TensorShape
}

object Field {
  implicit val fieldEq: Eq[Field] = (x: Field, y: Field) => x.name === y.name && x.shape === y.shape

  @JsonCodec
  final case class Tensor(
      name: String,
      dtype: DataType,
      shape: TensorShape,
      profile: Option[DataProfileType] = None
  ) extends Field

  @JsonCodec
  final case class Map(name: String, subfields: List[Field], shape: TensorShape) extends Field

  def toProto(field: Field): ModelField =
    field match {
      case Tensor(name, dtype, shape, profile) =>
        ModelField(
          name = name,
          shape = TensorShape.toProto(shape),
          typeOrSubfields = ModelField.TypeOrSubfields.Dtype(DataType.toProto(dtype)),
          profile = profile.map(DataProfileType.toProto).getOrElse(DataProfileType.protoNone)
        )
      case Map(name, subfields, shape) =>
        ModelField(
          name = name,
          shape = TensorShape.toProto(shape),
          typeOrSubfields =
            ModelField.TypeOrSubfields.Subfields(ModelField.Subfield(subfields.map(Field.toProto))),
          profile = DataProfileType.protoNone
        )
    }

  def fromProto(pField: ModelField): EitherNec[String, Field] =
    pField.typeOrSubfields match {
      case TypeOrSubfields.Empty => Either.leftNec("Empty value instead of field")
      case TypeOrSubfields.Subfields(value) =>
        value.data.toList.traverse(fromProto).map { fields =>
          Field
            .Map(
              name = pField.name,
              subfields = fields,
              shape = TensorShape.fromProto(pField.shape)
            )
        }
      case TypeOrSubfields.Dtype(value) =>
        (
          DataType.fromProto(value).toValidatedNec,
          DataProfileType.fromProto(pField.profile).toValidatedNec
        ).mapN { (dtype, profile) =>
          Field.Tensor(
            name = pField.name,
            dtype = dtype,
            shape = TensorShape.fromProto(pField.shape),
            profile = profile
          )
        }.toEither
    }

}
