package io.hydrosphere.serving.manager.domain.tensor

import io.hydrosphere.serving.manager.domain.contract.TensorShape
import io.hydrosphere.serving.tensorflow.tensor.{MapTensorData, TensorProto}
import io.hydrosphere.serving.tensorflow.types.DataType

case class MapTensor(shape: TensorShape, data: Seq[Map[String, TypedTensor[_]]])
    extends TypedTensor[DataType.DT_MAP.type] {
  override type Self = MapTensor

  override type DataT = Map[String, TypedTensor[_]]

  override def dtype = DataType.DT_MAP

  override def factory = MapTensor
}

object MapTensor extends TypedTensorFactory[MapTensor] {
  implicit override def lens: TensorProtoLens[MapTensor] =
    new TensorProtoLens[MapTensor] {
      override def getter: TensorProto => Seq[Map[String, TypedTensor[_]]] = { tensor =>
        tensor.mapVal.map {
          _.subtensors.mapValues(TypedTensorFactory.create).toMap
        }
      }

      override def setter: (TensorProto, Seq[Map[String, TypedTensor[_]]]) => TensorProto = {
        (tensor, maps) =>
          val protoMaps = maps.map { tensorMap =>
            MapTensorData(tensorMap.mapValues(_.toProto).toMap)
          }
          tensor.withMapVal(protoMaps)
      }
    }

  override def constructor = MapTensor.apply
}
