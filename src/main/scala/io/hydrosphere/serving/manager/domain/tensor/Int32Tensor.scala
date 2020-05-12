package io.hydrosphere.serving.manager.domain.tensor

import io.hydrosphere.serving.manager.domain.contract.TensorShape
import io.hydrosphere.serving.tensorflow.types.DataType

case class Int32Tensor(shape: TensorShape, data: Seq[Int])
    extends IntTensor[DataType.DT_INT32.type] {
  override type Self = Int32Tensor

  override def dtype = DataType.DT_INT32

  override def factory = Int32Tensor
}

object Int32Tensor extends TypedTensorFactory[Int32Tensor] {
  implicit override def lens: TensorProtoLens[Int32Tensor] = IntTensor.protoLens[Int32Tensor]

  override def constructor = Int32Tensor.apply
}
