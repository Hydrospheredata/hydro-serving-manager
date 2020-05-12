package io.hydrosphere.serving.manager.domain.tensor

import io.hydrosphere.serving.tensorflow.tensor.TensorProto
import io.hydrosphere.serving.tensorflow.types.DataType

trait IntTensor[T <: DataType] extends TypedTensor[T] {
  final override type DataT = Int
}

object IntTensor {
  def protoLens[T <: IntTensor[_]] =
    new TensorProtoLens[T] {
      override def getter: TensorProto => Seq[Int] = _.intVal

      override def setter: (TensorProto, Seq[Int]) => TensorProto = _.withIntVal(_)
    }
}
