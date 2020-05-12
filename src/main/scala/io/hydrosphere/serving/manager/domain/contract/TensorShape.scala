package io.hydrosphere.serving.manager.domain.contract

import io.circe.generic.JsonCodec
import io.hydrosphere.serving.tensorflow.tensor_shape.TensorShapeProto

/**
  * If Some, then acts like a numpy ndarray shape
  * If None, then there is no static shape. Checks disabled.
  */
@JsonCodec
sealed trait TensorShape extends Product with Serializable

case object TensorShape {
  case class Static(dims: List[Long]) extends TensorShape
  case object Dynamic                 extends TensorShape

  final val infiniteDim = -1

  def scalar: Static = Static(List())

  def vector(size: Long): Static = Static(List(size))

  def varVector: Static = Static(List(infiniteDim))

  def mat(dims: Long*): Static = Static(dims.toList)

  def toProto(tensorShape: TensorShape): Option[TensorShapeProto] =
    tensorShape match {
      case Static(dims) => Some(TensorShapeProto(dim = dims.map(d => TensorShapeProto.Dim(d))))
      case Dynamic      => None
    }

  def fromProto(protoShape: Option[TensorShapeProto]): TensorShape =
    protoShape match {
      case Some(value) => Static(value.dim.map(_.size).toList)
      case None        => Dynamic
    }
}
