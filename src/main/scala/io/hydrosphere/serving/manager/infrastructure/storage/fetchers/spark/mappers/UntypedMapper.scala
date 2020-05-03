package io.hydrosphere.serving.manager.infrastructure.storage.fetchers.spark.mappers

import io.hydrosphere.serving.manager.domain.contract.{DataType, Field, TensorShape}
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.spark.SparkModelMetadata

class UntypedMapper(m: SparkModelMetadata) extends SparkMlTypeMapper(m) {
  private[this] val inputCols = Array("inputCol", "featuresCol")
  private[this] val outputCols =
    Array("outputCol", "predictionCol", "probabilityCol", "rawPredictionCol")
  private[this] val labelCol = "labelCol"

  override def labelSchema: Option[Field] = {
    m.getParam[String]("labelCol")
      .map { name =>
        Field.Tensor(
          name = name,
          dtype = DataType.DT_STRING,
          shape = TensorShape.Dynamic,
          profile = None
        )
      }
  }

  override def inputSchema: Option[List[Field]] = {
    Some(
      inputCols
        .map(m.getParam[String])
        .flatMap {
          _.map { inputName =>
            Field.Tensor(
              name = inputName,
              dtype = DataType.DT_STRING,
              shape = TensorShape.Dynamic,
              profile = None
            )
          }
        }
        .toList
    )
  }

  override def outputSchema: Option[List[Field]] = {
    Some(
      outputCols
        .map(m.getParam[String])
        .flatMap {
          _.map { inputName =>
            Field.Tensor(
              name = inputName,
              dtype = DataType.DT_STRING,
              shape = TensorShape.Dynamic,
              profile = None
            )
          }
        }
        .toList
    )
  }
}
