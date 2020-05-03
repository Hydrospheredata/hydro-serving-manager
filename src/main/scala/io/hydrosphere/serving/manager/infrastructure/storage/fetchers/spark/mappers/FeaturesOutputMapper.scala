package io.hydrosphere.serving.manager.infrastructure.storage.fetchers.spark.mappers

import io.hydrosphere.serving.manager.domain.contract.Field
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.FieldInfo
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.spark.SparkModelMetadata

abstract class FeaturesOutputMapper(m: SparkModelMetadata) extends SparkMlTypeMapper(m) {
  def featuresType(sparkModelMetadata: SparkModelMetadata): FieldInfo

  def outputType(sparkModelMetadata: SparkModelMetadata): FieldInfo

  final def inputSchema: Option[List[Field]] = {
    m.getParam[String]("featuresCol")
      .map { name =>
        List(
          Field.Tensor(
            name = name,
            dtype = featuresType(m).dataType,
            shape = featuresType(m).shape,
            profile = None
          )
        )
      }
  }

  final def outputSchema: Option[List[Field]] = {
    m.getParam[String]("outputCol")
      .map { name =>
        List(
          Field.Tensor(
            name = name,
            dtype = outputType(m).dataType,
            shape = outputType(m).shape,
            profile = None
          )
        )
      }
  }
}
