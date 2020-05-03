package io.hydrosphere.serving.manager.domain.contract

import io.circe.generic.JsonCodec

@JsonCodec
sealed trait Field extends Product with Serializable {
  def name: String
}

object Field {

  @JsonCodec
  final case class Tensor(
      name: String,
      dtype: DataType,
      shape: TensorShape,
      profile: Option[DataProfileType]
  ) extends Field

  @JsonCodec
  final case class Map(name: String, subfields: Field, shape: TensorShape) extends Field

}
