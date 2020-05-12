package io.hydrosphere.serving.manager.domain.tensor

import io.hydrosphere.serving.manager.domain.contract.TensorShape
import io.hydrosphere.serving.tensorflow.types.DataType

case class Uint8Tensor(shape: TensorShape, data: Seq[Int])
    extends IntTensor[DataType.DT_UINT8.type] {
  override type Self = Uint8Tensor

  override def dtype = DataType.DT_UINT8

  override def factory = Uint8Tensor
}

object Uint8Tensor extends TypedTensorFactory[Uint8Tensor] {
  implicit override def lens: TensorProtoLens[Uint8Tensor] = IntTensor.protoLens[Uint8Tensor]

  override def constructor = Uint8Tensor.apply
}
