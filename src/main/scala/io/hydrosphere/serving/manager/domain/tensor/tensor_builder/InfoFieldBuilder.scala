package io.hydrosphere.serving.manager.domain.tensor.tensor_builder

import io.circe.Json
import io.hydrosphere.serving.manager.domain.contract.Field
import io.hydrosphere.serving.manager.domain.contract.TensorShape.{Dynamic, Static}
import io.hydrosphere.serving.manager.domain.tensor._

class InfoFieldBuilder(val field: Field.Tensor) {

  def convert(data: Json): Either[ValidationError, TypedTensor[_]] =
    data match {
      case x: Json if x.isArray         => process(x.asArray.get)
      case str: Json if str.isString    => process(Seq(str))
      case num: Json if num.isNumber    => process(Seq(num))
      case bool: Json if bool.isBoolean => process(Seq(bool))
      case _                            => Left(ValidationError.IncompatibleFieldTypeError(field.name, field.dtype))
    }

  def process(data: Seq[Json]): Either[ValidationError, TypedTensor[_]] = {
    val reshapedData = field.shape match {
      case Static(_) => flatten(data)
      case Dynamic   => data
    }
    val factory = TypedTensorFactory(field.dtype)
    val convertedData = factory match {
      case FloatTensor | SComplexTensor =>
        reshapedData.map(_.asNumber.get.toFloat)
      case DoubleTensor | DComplexTensor =>
        reshapedData.map(_.asNumber.get.toDouble)
      case Uint64Tensor | Int64Tensor =>
        reshapedData.map(_.asNumber.flatMap(_.toLong).get)
      case Int8Tensor | Uint8Tensor | Int16Tensor | Uint16Tensor | Int32Tensor | Uint32Tensor =>
        reshapedData.map(_.asNumber.flatMap(_.toInt).get)
      case StringTensor => reshapedData.map(_.asString.get)
      case BoolTensor   => reshapedData.map(_.asBoolean.get)
    }
    toTensor(factory, convertedData)
  }

  def toTensor[T <: TypedTensor[_]](
      factory: TypedTensorFactory[T],
      flatData: Seq[Any]
  ): Either[ValidationError, T] =
    factory.createFromAny(flatData, field.shape) match {
      case Some(tensor) => Right(tensor)
      case None         => Left(ValidationError.IncompatibleFieldTypeError(field.name, field.dtype))
    }

  private def flatten(arr: Seq[Json]): Seq[Json] =
    arr.flatMap {
      case arr: Json if arr.isArray => flatten(arr.asArray.get)
      case value                    => List(value)
    }
}
