package io.hydrosphere.serving.manager.domain.tensor

import com.google.protobuf.ByteString
import io.hydrosphere.serving.manager.domain.contract.TensorShape
import io.hydrosphere.serving.tensorflow.tensor.TensorProto
import io.hydrosphere.serving.tensorflow.types.DataType

case class StringTensor(shape: TensorShape, data: Seq[String])
    extends TypedTensor[DataType.DT_STRING.type] {
  override type Self = StringTensor

  override type DataT = String

  override def dtype = DataType.DT_STRING

  override def factory = StringTensor
}

object StringTensor extends TypedTensorFactory[StringTensor] {
  implicit override def lens: TensorProtoLens[StringTensor] =
    new TensorProtoLens[StringTensor] {
      override def getter: TensorProto => Seq[String] = _.stringVal.map(_.toStringUtf8)

      override def setter: (TensorProto, Seq[String]) => TensorProto =
        (t, d) => t.withStringVal(d.map(ByteString.copyFromUtf8))
    }

  override def constructor = StringTensor.apply
}
