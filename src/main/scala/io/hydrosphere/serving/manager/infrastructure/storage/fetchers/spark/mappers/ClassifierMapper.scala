package io.hydrosphere.serving.manager.infrastructure.storage.fetchers.spark.mappers

import io.hydrosphere.serving.manager.domain.contract.Field
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.FieldInfo
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.spark.SparkModelMetadata

abstract class ClassifierMapper(m: SparkModelMetadata) extends PredictorMapper(m) {
  def rawPredictionType(sparkModelMetadata: SparkModelMetadata): FieldInfo =
    SparkMlTypeMapper.classesVec(sparkModelMetadata)

  val rawPredictionSchema: Option[List[Field]] =
    m.getParam[String]("rawPredictionCol")
      .map { name =>
        List(
          Field.Tensor(
            name = name,
            dtype = rawPredictionType(m).dataType,
            shape = rawPredictionType(m).shape,
            profile = None
          )
        )
      }

  override def outputSchema: Option[List[Field]] =
    for {
      sschema <- super.outputSchema
      pschema <- rawPredictionSchema
    } yield sschema ++ pschema
}
