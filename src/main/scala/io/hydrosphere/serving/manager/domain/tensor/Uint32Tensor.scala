package io.hydrosphere.serving.manager.domain.tensor

import io.hydrosphere.serving.manager.domain.contract.TensorShape
import io.hydrosphere.serving.tensorflow.tensor.TensorProto
import io.hydrosphere.serving.tensorflow.types.DataType

case class Uint32Tensor(shape: TensorShape, data: Seq[Int])
    extends IntTensor[DataType.DT_UINT32.type] {
  override type Self = Uint32Tensor

  override def dtype = DataType.DT_UINT32

  override def factory = Uint32Tensor
}

object Uint32Tensor extends TypedTensorFactory[Uint32Tensor] {
  implicit override def lens: TensorProtoLens[Uint32Tensor] =
    new TensorProtoLens[Uint32Tensor] {
      override def getter: TensorProto => Seq[Int] = _.uint32Val

      override def setter: (TensorProto, Seq[Int]) => TensorProto = _.withUint32Val(_)
    }

  override def constructor = Uint32Tensor.apply
}
