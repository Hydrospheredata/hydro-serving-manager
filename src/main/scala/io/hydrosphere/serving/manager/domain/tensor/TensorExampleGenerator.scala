package io.hydrosphere.serving.manager.domain.tensor

import io.circe.Json
import io.hydrosphere.serving.manager.domain.contract.DataType._
import io.hydrosphere.serving.manager.domain.contract.TensorShape.{Dynamic, Static}
import io.hydrosphere.serving.manager.domain.contract._
import io.hydrosphere.serving.manager.domain.tensor.json.TensorJsonLens

case class TensorExampleGenerator(modelApi: Signature) {
  def inputs: Map[String, TypedTensor[_]] =
    modelApi.inputs.toList.flatMap(TensorExampleGenerator.generateField).toMap

  def outputs: Map[String, TypedTensor[_]] =
    modelApi.outputs.toList.flatMap(TensorExampleGenerator.generateField).toMap
}

object TensorExampleGenerator {
  def generatePayload(contract: Contract): Json = {
    val x = TensorExampleGenerator
      .forPredict(contract)
    TensorJsonLens.mapToJson(x.inputs)
  }

  def forPredict(modelContract: Contract): TensorExampleGenerator =
    TensorExampleGenerator(modelContract.predict)

  def generateScalarData[T <: DataType](dataType: T): Any =
    dataType match {
      case DT_FLOAT | DT_COMPLEX64   => 1.0f
      case DT_DOUBLE | DT_COMPLEX128 => 1.0d
      case DT_INT8 | DT_INT16 | DT_INT32 | DT_UINT8 | DT_UINT16 | DT_UINT32 | DT_QINT8 | DT_QINT16 |
          DT_QINT32 | DT_QUINT8 | DT_QUINT16 =>
        1
      case DT_INT64 | DT_UINT64 => 1L
      case DT_STRING            => "foo"
      case DT_BOOL              => true

      case x => throw new IllegalArgumentException(s"Cannot process Tensor with $x dtype") // refs
    }

  def createFlatTensor[T](shape: TensorShape, generator: => T): Seq[T] =
    shape match {
      case Dynamic                      => List(generator)
      case Static(dims) if dims.isEmpty => List(generator)
      case Static(dims) =>
        val flatLen = dims.map(_.max(1)).product
        (1L to flatLen).map(_ => generator) // mat
    }

  def generateTensor(shape: TensorShape, dtype: DataType): Option[TypedTensor[_]] = {
    val factory = TypedTensorFactory(dtype)
    val data    = createFlatTensor(shape, generateScalarData(dtype))
    factory.createFromAny(data, shape)
  }

  def generateField(field: Field): Map[String, TypedTensor[_]] = {
    val fieldValue = field match {
      case Field.Tensor(_, dtype, shape, _) => generateTensor(shape, dtype)
      case Field.Map(_, subfields, shape)   => generateNestedTensor(shape, subfields)
    }
    fieldValue.map(x => field.name -> x).toMap
  }

  private def generateNestedTensor(
      shape: TensorShape,
      value: List[Field]
  ): Option[MapTensor] = {
    val map        = generateMap(value)
    val tensorData = createFlatTensor(shape, map)
    Some(MapTensor(shape, tensorData))
  }

  private def generateMap(value: List[Field]): Map[String, TypedTensor[_]] =
    value.flatMap(generateField).toMap
}
