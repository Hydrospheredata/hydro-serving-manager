package io.hydrosphere.serving.manager.domain.tensor.tensor_builder

import io.circe.Json
import io.hydrosphere.serving.manager.domain.contract.Field
import io.hydrosphere.serving.manager.domain.tensor.ValidationError.ComplexFieldValidationError
import io.hydrosphere.serving.manager.domain.tensor.{TypedTensor, ValidationError}

class ModelFieldBuilder(val modelField: Field) {

  def convert(data: Json): Either[ValidationError, TypedTensor[_]] =
    modelField match {
      case x: Field.Map =>
        val complexFieldValidator = new ComplexFieldBuilder(x)
        complexFieldValidator.convert(data).left.map { errors =>
          ComplexFieldValidationError(errors, x)
        }

      case x: Field.Tensor =>
        val infoFieldValidator = new InfoFieldBuilder(x)
        infoFieldValidator.convert(data)
    }

}
