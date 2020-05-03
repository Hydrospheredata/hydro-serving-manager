package io.hydrosphere.serving.manager.infrastructure.storage.fetchers.spark.mappers

import io.hydrosphere.serving.manager.domain.contract.{DataType, Field, TensorShape}
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.FieldInfo
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.spark._

abstract class SparkMlTypeMapper(val m: SparkModelMetadata) {
  def inputSchema: Option[List[Field]]
  def outputSchema: Option[List[Field]]
  def labelSchema: Option[Field] = None
}

object SparkMlTypeMapper {

  def featuresVec(sparkModelMetadata: SparkModelMetadata): FieldInfo = {
    FieldInfo(
      DataType.DT_DOUBLE,
      TensorShape.fixedVector(sparkModelMetadata.numFeatures.getOrElse(-1).toLong)
    )
  }

  def classesVec(sparkModelMetadata: SparkModelMetadata): FieldInfo = {
    FieldInfo(
      DataType.DT_DOUBLE,
      TensorShape.fixedVector(sparkModelMetadata.numFeatures.getOrElse(-1).toLong)
    )
  }

  def apply(sparkModelMetadata: SparkModelMetadata): SparkMlTypeMapper = {
    sparkModelMetadata.`class` match {
      case "org.apache.spark.ml.feature.HashingTF"     => new HashingTFMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.IDF"           => new IDFMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.Word2VecModel" => new Word2VecMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.CountVectorizerModel" =>
        new CountVectorizerMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.Tokenizer"      => new TokenizerMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.RegexTokenizer" => new TokenizerMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.StopWordsRemover" =>
        new StopWordsRemoverMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.NGram"     => new NGramMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.Binarizer" => new BinarizerMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.PCAModel"  => new PCAMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.PolynomialExpansion" =>
        new PolynomialExpansionMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.DCT" => new DCTMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.StringIndexerModel" =>
        new StringIndexerMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.IndexToString" =>
        new IndexToStringMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.OneHotEncoder" =>
        new OneHotEncoderMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.VectorIndexerModel" =>
        new VectorIndexerMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.Interaction" => new InteractionMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.Normalizer"  => new NormalizerMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.StandardScalerModel" =>
        new StandardScalerMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.MinMaxScalerModel" =>
        new MinMaxScalerMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.MaxAbsScalerModel" =>
        new MaxAbsScalerMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.Bucketizer" => new BucketizerMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.ElementwiseProduct" =>
        new ElementwiseProductMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.VectorAssembler" =>
        new VectorAssemblerMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.VectorSlicer" => new VectorSlicerMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.ChiSqSelectorModel" =>
        new ChiSqSelectorMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.BucketedRandomProjectionLSHModel" =>
        new BucketedRandomProjectionLSHMapper(sparkModelMetadata)
      case "org.apache.spark.ml.feature.MinHashLSHModel" => new MinHashLSH(sparkModelMetadata)

      case "org.apache.spark.ml.classification.LogisticRegressionModel" =>
        new LogisticRegression(sparkModelMetadata)
      case "org.apache.spark.ml.classification.DecisionTreeClassificationModel" =>
        new DecisionTreeClassifierMapper(sparkModelMetadata)
      case "org.apache.spark.ml.classification.RandomForestClassificationModel" =>
        new RandomForestClassifierMapper(sparkModelMetadata)
      case "org.apache.spark.ml.classification.GBTClassificationModel" =>
        new GBTClassifierMapper(sparkModelMetadata)
      case "org.apache.spark.ml.classification.MultilayerPerceptronClassificationModel" =>
        new MultilayerPerceptronClassificationMapper(sparkModelMetadata)
      case "org.apache.spark.ml.classification.LinearSVCModel" =>
        new LinearSVCMapper(sparkModelMetadata)
      case "org.apache.spark.ml.classification.NaiveBayesModel" =>
        new NaiveBayesMapper(sparkModelMetadata)

      case "org.apache.spark.ml.regression.LinearRegressionModel" =>
        new LinearRegressionMapper(sparkModelMetadata)
      case "org.apache.spark.ml.regression.GeneralizedLinearRegressionModel" =>
        new GeneralizedLinearRegressionMapper(sparkModelMetadata)
      case "org.apache.spark.ml.regression.DecisionTreeRegressionModel" =>
        new DecisionTreeRegressionMapper(sparkModelMetadata)
      case "org.apache.spark.ml.regression.RandomForestRegressionModel" =>
        new RandomForestRegressionMapper(sparkModelMetadata)
      case "org.apache.spark.ml.regression.GBTRegressionModel" =>
        new GBTRegressionMapper(sparkModelMetadata)
      case "org.apache.spark.ml.regression.AFTSurvivalRegressionModel" =>
        new AFTSurvivalRegressionMapper(sparkModelMetadata)
      case "org.apache.spark.ml.regression.IsotonicRegressionModel" =>
        new IsotonicRegressionMapper(sparkModelMetadata)

      case "org.apache.spark.ml.clustering.KMeansModel" => new KMeansMapper(sparkModelMetadata)
      case _                                            => new UntypedMapper(sparkModelMetadata)
    }
  }
}
