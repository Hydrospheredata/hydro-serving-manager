package io.hydrosphere.serving.manager.domain.tensor

import io.hydrosphere.serving.manager.domain.contract.TensorShape
import io.hydrosphere.serving.tensorflow.tensor.TensorProto
import io.hydrosphere.serving.tensorflow.types.DataType

trait TypedTensor[DTypeT] {
  type Self <: TypedTensor[DTypeT]
  type DataT

  def data: Seq[Self#DataT]

  def shape: TensorShape

  def dtype: DataType

  def factory: TypedTensorFactory[Self]

  final def toProto: TensorProto = {
    val pretensor = TensorProto(dtype, TensorShape.toProto(shape))
    pretensor.update(_ => factory.lens.lens := data)
  }
}
