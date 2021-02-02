package io.hydrosphere.serving.manager.domain.contract

import io.circe.generic.JsonCodec
import io.hydrosphere.serving.proto.contract.tensor.{TensorShape => GTensorShape}

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

  def toProto(tensorShape: TensorShape): Option[GTensorShape] =
    tensorShape match {
      case Static(dims) => Some(GTensorShape(dims))
      case Dynamic      => None
    }

  def fromProto(protoShape: Option[GTensorShape]): TensorShape =
    protoShape match {
      case Some(value) => Static(value.dims.toList)
      case None        => Dynamic
    }
}
