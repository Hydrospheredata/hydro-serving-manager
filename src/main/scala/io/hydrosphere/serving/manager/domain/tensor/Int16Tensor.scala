package io.hydrosphere.serving.manager.domain.tensor

import io.hydrosphere.serving.manager.domain.contract.TensorShape
import io.hydrosphere.serving.tensorflow.types.DataType

case class Int16Tensor(shape: TensorShape, data: Seq[Int])
    extends IntTensor[DataType.DT_INT16.type] {
  override type Self = Int16Tensor

  override def dtype = DataType.DT_INT16

  override def factory = Int16Tensor
}

object Int16Tensor extends TypedTensorFactory[Int16Tensor] {
  implicit override def lens: TensorProtoLens[Int16Tensor] = IntTensor.protoLens[Int16Tensor]

  override def constructor = Int16Tensor.apply
}
