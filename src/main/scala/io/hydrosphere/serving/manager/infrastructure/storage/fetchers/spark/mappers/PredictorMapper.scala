package io.hydrosphere.serving.manager.infrastructure.storage.fetchers.spark.mappers

import io.hydrosphere.serving.manager.domain.contract.{DataType, Field, TensorShape}
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.FieldInfo
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.spark.SparkModelMetadata

abstract class PredictorMapper(m: SparkModelMetadata) extends SparkMlTypeMapper(m) {
  def featuresType(sparkModelMetadata: SparkModelMetadata): FieldInfo =
    SparkMlTypeMapper.featuresVec(sparkModelMetadata)

  def predictionType(sparkModelMetadata: SparkModelMetadata): FieldInfo =
    FieldInfo(DataType.DT_DOUBLE, TensorShape.scalar)

  override def labelSchema: Option[Field] =
    m.getParam[String]("labelCol").map { name =>
      Field.Tensor(
        name = name,
        dtype = DataType.DT_STRING,
        shape = TensorShape.Dynamic,
        profile = None
      )
    }

  override def inputSchema: Option[List[Field]] =
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

  override def outputSchema: Option[List[Field]] =
    m.getParam[String]("predictionCol")
      .map { name =>
        List(
          Field.Tensor(
            name = name,
            dtype = predictionType(m).dataType,
            shape = predictionType(m).shape,
            profile = None
          )
        )
      }
}
