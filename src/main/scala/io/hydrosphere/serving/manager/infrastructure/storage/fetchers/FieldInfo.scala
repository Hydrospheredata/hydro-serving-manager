package io.hydrosphere.serving.manager.infrastructure.storage.fetchers

import io.hydrosphere.serving.manager.domain.contract.{DataType, Field, TensorShape}

case class FieldInfo(dataType: DataType, shape: TensorShape) {
  def toField(name: String): Field.Tensor = {
    Field.Tensor(
      name = name,
      shape = shape,
      dtype = dataType,
      profile = None
    )
  }
}
