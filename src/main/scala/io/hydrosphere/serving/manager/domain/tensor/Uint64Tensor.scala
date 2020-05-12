package io.hydrosphere.serving.manager.domain.tensor

import io.hydrosphere.serving.manager.domain.contract.TensorShape
import io.hydrosphere.serving.tensorflow.tensor.TensorProto
import io.hydrosphere.serving.tensorflow.types.DataType

case class Uint64Tensor(shape: TensorShape, data: Seq[Long])
    extends TypedTensor[DataType.DT_UINT64.type] {
  override type Self  = Uint64Tensor
  override type DataT = Long

  override def dtype = DataType.DT_UINT64

  override def factory = Uint64Tensor
}

object Uint64Tensor extends TypedTensorFactory[Uint64Tensor] {
  implicit override def lens: TensorProtoLens[Uint64Tensor] =
    new TensorProtoLens[Uint64Tensor] {
      override def getter: TensorProto => Seq[Long] = _.uint64Val

      override def setter: (TensorProto, Seq[Long]) => TensorProto = _.withUint64Val(_)
    }

  override def constructor = Uint64Tensor.apply
}
