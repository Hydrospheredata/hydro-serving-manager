package io.hydrosphere.serving.manager.domain.contract

import cats.Eq
import io.circe.generic.JsonCodec
import io.hydrosphere.serving.proto.contract.tensor.{TensorShape => GTensorShape}
import cats.syntax.eq._

/**
  * If Some, then acts like a numpy ndarray shape
  * If None, then there is no static shape. Checks disabled.
  */
sealed trait TensorShape extends Product with Serializable

case object TensorShape {
  @JsonCodec
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

  implicit val tensorShapeEq: Eq[TensorShape] = (x: TensorShape, y: TensorShape) =>
    (x, y) match {
      case (TensorShape.Dynamic, TensorShape.Dynamic)             => x == y
      case (TensorShape.Static(dims1), TensorShape.Static(dims2)) => dims1 === dims2
      case _                                                      => false
    }
}
