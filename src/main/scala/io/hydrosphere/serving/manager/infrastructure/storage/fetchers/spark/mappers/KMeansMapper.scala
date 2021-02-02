package io.hydrosphere.serving.manager.infrastructure.storage.fetchers.spark.mappers

import io.hydrosphere.serving.manager.domain.contract.{DataType, TensorShape}
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.FieldInfo
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.spark.SparkModelMetadata

class KMeansMapper(m: SparkModelMetadata) extends PredictorMapper(m) {
  override def labelSchema = None
  override def predictionType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_INT32, TensorShape.scalar)
}
