package io.hydrosphere.serving.manager.domain.tensor

import io.hydrosphere.serving.manager.domain.contract.TensorShape
import io.hydrosphere.serving.tensorflow.tensor.TensorProto
import io.hydrosphere.serving.tensorflow.types.DataType

case class DoubleTensor(shape: TensorShape, data: Seq[Double])
    extends TypedTensor[DataType.DT_DOUBLE.type] {
  override type Self = DoubleTensor

  override type DataT = Double

  override def dtype = DataType.DT_DOUBLE

  override def factory = DoubleTensor
}

object DoubleTensor extends TypedTensorFactory[DoubleTensor] {
  implicit override def lens: TensorProtoLens[DoubleTensor] =
    new TensorProtoLens[DoubleTensor] {
      override def getter: TensorProto => Seq[Double] = _.doubleVal

      override def setter: (TensorProto, Seq[Double]) => TensorProto = _.withDoubleVal(_)
    }

  override def constructor = DoubleTensor.apply
}
