package io.hydrosphere.serving.manager.domain.tensor

import io.hydrosphere.serving.manager.domain.contract.TensorShape
import io.hydrosphere.serving.tensorflow.tensor.TensorProto
import io.hydrosphere.serving.tensorflow.types.DataType

case class Int64Tensor(shape: TensorShape, data: Seq[Long])
    extends TypedTensor[DataType.DT_INT64.type] {
  override type Self = Int64Tensor

  override type DataT = Long

  override def dtype = DataType.DT_INT64

  override def factory = Int64Tensor
}

object Int64Tensor extends TypedTensorFactory[Int64Tensor] {
  implicit override def lens: TensorProtoLens[Int64Tensor] =
    new TensorProtoLens[Int64Tensor] {
      override def getter: TensorProto => Seq[Long] = _.int64Val

      override def setter: (TensorProto, Seq[Long]) => TensorProto = _.withInt64Val(_)
    }

  override def constructor = Int64Tensor.apply
}
