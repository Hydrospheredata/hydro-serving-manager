package io.hydrosphere.serving.manager.infrastructure.storage.fetchers.spark.mappers

import io.hydrosphere.serving.manager.domain.contract.{DataType, Field, TensorShape}
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.FieldInfo
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.spark.SparkModelMetadata

class HashingTFMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_STRING, TensorShape.varVector)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_DOUBLE, TensorShape.varVector)
}
class IDFMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    SparkMlTypeMapper.featuresVec(sparkModelMetadata)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_DOUBLE, TensorShape.varVector)
}
class Word2VecMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  def inputType(metadata: SparkModelMetadata) = FieldInfo(DataType.DT_STRING, TensorShape.varVector)

  def outputType(metadata: SparkModelMetadata) = FieldInfo(
    DataType.DT_DOUBLE,
    TensorShape.fixedVector(
      metadata.getParam[Double]("vectorSize").getOrElse(-1.0).toLong
    ) // NB Spark uses doubles to store vector length
  )
}
class CountVectorizerMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_STRING, TensorShape.varVector)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_DOUBLE, TensorShape.varVector)
}
class TokenizerMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_STRING, TensorShape.scalar)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_STRING, TensorShape.varVector)
}
class StopWordsRemoverMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_STRING, TensorShape.varVector)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_STRING, TensorShape.varVector)
}
class NGramMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_STRING, TensorShape.varVector)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_STRING, TensorShape.varVector)
}
class BinarizerMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_DOUBLE, TensorShape.scalar)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_DOUBLE, TensorShape.scalar)
}
class PCAMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    SparkMlTypeMapper.featuresVec(sparkModelMetadata)

  override def outputType(sparkModelMetadata: SparkModelMetadata) = FieldInfo(
    DataType.DT_DOUBLE,
    TensorShape.fixedVector(sparkModelMetadata.getParam[Double]("k").getOrElse(-1.0).toLong)
  )
}
class PolynomialExpansionMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    SparkMlTypeMapper.featuresVec(sparkModelMetadata)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_DOUBLE, TensorShape.varVector)
}
class DCTMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    SparkMlTypeMapper.featuresVec(sparkModelMetadata)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_DOUBLE, TensorShape.varVector)
}
class StringIndexerMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_STRING, TensorShape.scalar)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_DOUBLE, TensorShape.scalar)
}
class IndexToStringMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_DOUBLE, TensorShape.scalar)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_STRING, TensorShape.scalar)
}
class OneHotEncoderMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_STRING, TensorShape.scalar)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_DOUBLE, TensorShape.varVector)
}
class VectorIndexerMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    SparkMlTypeMapper.featuresVec(sparkModelMetadata)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_DOUBLE, TensorShape.scalar)
}
class InteractionMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    SparkMlTypeMapper.featuresVec(sparkModelMetadata)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_DOUBLE, TensorShape.varVector)
}
class NormalizerMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    SparkMlTypeMapper.featuresVec(sparkModelMetadata)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_DOUBLE, TensorShape.varVector)
}
class StandardScalerMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    SparkMlTypeMapper.featuresVec(sparkModelMetadata)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_DOUBLE, TensorShape.varVector)
}
class MinMaxScalerMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    SparkMlTypeMapper.featuresVec(sparkModelMetadata)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_DOUBLE, TensorShape.varVector)
}
class MaxAbsScalerMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    SparkMlTypeMapper.featuresVec(sparkModelMetadata)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_DOUBLE, TensorShape.varVector)
}
class BucketizerMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_DOUBLE, TensorShape.scalar)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_DOUBLE, TensorShape.scalar)
}
class ElementwiseProductMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    SparkMlTypeMapper.featuresVec(sparkModelMetadata)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_DOUBLE, TensorShape.varVector)
}
class VectorAssemblerMapper(m: SparkModelMetadata) extends SparkMlTypeMapper(m) {
  override def inputSchema: Option[List[Field]] = {
    m.getParam[Seq[String]]("inputCols")
      .map {
        _.map { col =>
          Field.Tensor(
            name = col,
            dtype = DataType.DT_VARIANT,
            shape = TensorShape.Dynamic,
            profile = None
          )
        }.toList
      }
  }

  override def outputSchema: Option[List[Field]] = {
    m.getParam[String]("outputCol")
      .map { name =>
        List(
          Field.Tensor(
            name = name,
            dtype = SparkMlTypeMapper.featuresVec(m).dataType,
            shape = SparkMlTypeMapper.featuresVec(m).shape,
            profile = None
          )
        )
      }
  }
}
class VectorSlicerMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_VARIANT, TensorShape.varVector)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_VARIANT, TensorShape.varVector)
}
class ChiSqSelectorMapper(m: SparkModelMetadata) extends FeaturesOutputMapper(m) {
  override def featuresType(sparkModelMetadata: SparkModelMetadata) =
    SparkMlTypeMapper.featuresVec(sparkModelMetadata)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_DOUBLE, TensorShape.varVector)
}
class BucketedRandomProjectionLSHMapper(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    SparkMlTypeMapper.featuresVec(sparkModelMetadata)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_DOUBLE, TensorShape.varVector)
}
class MinHashLSH(m: SparkModelMetadata) extends InputOutputMapper(m) {
  override def inputType(sparkModelMetadata: SparkModelMetadata) =
    SparkMlTypeMapper.featuresVec(sparkModelMetadata)

  override def outputType(sparkModelMetadata: SparkModelMetadata) =
    FieldInfo(DataType.DT_DOUBLE, TensorShape.varVector)
}
