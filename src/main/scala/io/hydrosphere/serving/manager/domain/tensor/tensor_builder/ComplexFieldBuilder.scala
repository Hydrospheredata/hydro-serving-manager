package io.hydrosphere.serving.manager.domain.tensor.tensor_builder

import io.circe.Json
import io.hydrosphere.serving.manager.domain.contract.{DataType, Field}
import io.hydrosphere.serving.manager.domain.tensor
import io.hydrosphere.serving.manager.domain.tensor.ValidationError.{
  FieldMissingError,
  IncompatibleFieldTypeError
}
import io.hydrosphere.serving.manager.domain.tensor.{MapTensor, ValidationError}

class ComplexFieldBuilder(val modelField: Field.Map) {

  def convert(data: Json): Either[Seq[ValidationError], MapTensor] =
    data match {
      case arr: Json if arr.isArray =>
        val eithers = arr.asArray.get.map(convert)
        val errors = eithers.collect {
          case Left(value) => value
        }.flatten

        if (errors.nonEmpty)
          Left(errors)
        else {
          val results = eithers.collect {
            case Right(value) => value
          }
          Right(
            MapTensor(
              modelField.shape,
              results.map(_.data.head)
            )
          )
        }

      case obj: Json if obj.isObject =>
        val a = modelField.subfields.map { field =>
          obj.asObject.get(field.name) match {
            case None => Left(FieldMissingError(field.name))

            case Some(fieldData) =>
              val fieldValidator = new ModelFieldBuilder(field)
              fieldValidator.convert(fieldData).map(tensor => field.name -> tensor)
          }
        }
        val errors = a.collect { case Left(err) => err }
        if (a.exists(_.isLeft))
          Left(errors)
        else {
          val tensors = a.collect { case Right((name, tensor)) => name -> tensor }.toMap
          Right(
            tensor.MapTensor(
              modelField.shape,
              Seq(tensors)
            )
          )
        }

      case _ => Left(Seq(IncompatibleFieldTypeError(modelField.name, DataType.DT_VARIANT)))
    }

}
