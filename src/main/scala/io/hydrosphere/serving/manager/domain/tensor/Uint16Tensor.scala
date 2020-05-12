package io.hydrosphere.serving.manager.domain.tensor

import io.hydrosphere.serving.manager.domain.contract.TensorShape
import io.hydrosphere.serving.tensorflow.types.DataType

case class Uint16Tensor(shape: TensorShape, data: Seq[Int])
    extends IntTensor[DataType.DT_UINT16.type] {
  override type Self = Uint16Tensor

  override def dtype = DataType.DT_UINT16

  override def factory = Uint16Tensor
}

object Uint16Tensor extends TypedTensorFactory[Uint16Tensor] {
  implicit override def lens: TensorProtoLens[Uint16Tensor] = IntTensor.protoLens[Uint16Tensor]

  override def constructor = Uint16Tensor.apply
}
