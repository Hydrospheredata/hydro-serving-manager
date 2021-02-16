package io.hydrosphere.serving.manager.infrastructure.storage.fetchers.spark.mappers

import io.hydrosphere.serving.manager.domain.contract.Field
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.FieldInfo
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.spark.SparkModelMetadata

abstract class ProbabilisticClassifierMapper(m: SparkModelMetadata) extends ClassifierMapper(m) {
  def probabilityType(sparkModelMetadata: SparkModelMetadata): FieldInfo =
    SparkMlTypeMapper.classesVec(sparkModelMetadata)

  val probalSchema: Option[List[Field]] =
    m.getParam[String]("probabilityCol")
      .map { name =>
        List(
          Field.Tensor(
            name = name,
            dtype = probabilityType(m).dataType,
            shape = probabilityType(m).shape,
            profile = None
          )
        )
      }

  override def outputSchema: Option[List[Field]] =
    for {
      sschema <- super.outputSchema
      pschema <- probalSchema
    } yield sschema ++ pschema
}
