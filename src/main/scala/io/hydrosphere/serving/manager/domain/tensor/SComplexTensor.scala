package io.hydrosphere.serving.manager.domain.tensor

import io.hydrosphere.serving.manager.domain.contract.TensorShape
import io.hydrosphere.serving.tensorflow.tensor.TensorProto
import io.hydrosphere.serving.tensorflow.types.DataType

case class SComplexTensor(shape: TensorShape, data: Seq[Float])
    extends TypedTensor[DataType.DT_COMPLEX64.type] {
  override type Self = SComplexTensor

  override type DataT = Float

  override def dtype = DataType.DT_COMPLEX64

  override def factory = SComplexTensor
}

object SComplexTensor extends TypedTensorFactory[SComplexTensor] {
  implicit override def lens: TensorProtoLens[SComplexTensor] =
    new TensorProtoLens[SComplexTensor] {
      override def getter: TensorProto => Seq[Float] = _.scomplexVal

      override def setter: (TensorProto, Seq[Float]) => TensorProto = _.withScomplexVal(_)
    }

  override def constructor = SComplexTensor.apply
}
