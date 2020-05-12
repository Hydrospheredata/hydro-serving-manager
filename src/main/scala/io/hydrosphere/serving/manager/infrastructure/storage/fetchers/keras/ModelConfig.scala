package io.hydrosphere.serving.manager.infrastructure.storage.fetchers.keras

import cats.data.NonEmptyList
import io.circe.Json
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}
import io.hydrosphere.serving.manager.domain.contract.{DataType, Field, Signature, TensorShape}
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.tensorflow.TypeMapper

@ConfiguredJsonCodec
sealed private[keras] trait ModelConfig {
  def toPredictSignature: Option[Signature]
}

private[keras] object ModelConfig {
  implicit val config: Configuration =
    Configuration.default.withSnakeCaseMemberNames.withDiscriminator("class_name")

  @ConfiguredJsonCodec
  case class Model(config: FunctionalModelConfig) extends ModelConfig {
    override def toPredictSignature: Option[Signature] = {
      // first element of the array is layer name
      val inputNames = config.inputLayers
        .map(_.head.asString)
        .collect { case Some(value) => value }

      val outputNames =
        config.outputLayers
          .map(_.head.asString)
          .collect { case Some(value) => value }

      val inputLayers  = config.layers.filter(l => inputNames.contains(l.name))
      val outputLayers = config.layers.filter(l => outputNames.contains(l.name))

      println(outputNames)
      println(outputLayers.map(_.config.field))

      for {
        inputs  <- NonEmptyList.fromList(inputLayers.map(_.config.field))
        outputs <- NonEmptyList.fromList(outputLayers.map(_.config.field))
      } yield Signature(
        signatureName = "Predict",
        inputs = inputs,
        outputs = outputs
      )
    }
  }

  @ConfiguredJsonCodec
  case class FunctionalModelConfig(
      name: String,
      layers: List[FunctionalLayerConfig],
      inputLayers: List[List[Json]],
      outputLayers: List[List[Json]]
  )

  @ConfiguredJsonCodec
  case class FunctionalLayerConfig(
      name: String,
      className: String,
      config: LayerConfig
//      inboundNodes: Json
  )

  @ConfiguredJsonCodec
  case class Sequential(config: List[SequentialLayerConfig]) extends ModelConfig {
    override def toPredictSignature: Option[Signature] =
      for {
        firstLayer <- config.headOption
        lastLayer  <- config.lastOption
        input  = firstLayer.config.field
        output = lastLayer.config.field
      } yield Signature(
        signatureName = "Predict",
        inputs = NonEmptyList.of(input),
        outputs = NonEmptyList.of(output)
      )
  }

  @ConfiguredJsonCodec
  case class SequentialLayerConfig(className: String, config: LayerConfig)

  @ConfiguredJsonCodec
  case class LayerConfig(
      name: String,
      dtype: Option[String],
      batchInputShape: Option[List[Json]],
      units: Option[Long],
      targetShape: Option[List[Json]]
  ) {
    def getShape: TensorShape = {
      val arrDims = batchInputShape.orElse(targetShape).map {
        case Nil => TensorShape.scalar
        case list =>
          val dims = list.map(x => x.asNumber.flatMap(_.toLong).getOrElse(-1L))
          TensorShape.Static(dims)
      }
      val scalarDims = units.map(u => TensorShape.Static(List(-1, u)))

      arrDims.orElse(scalarDims).getOrElse(TensorShape.Dynamic)
    }

    def field: Field =
      Field.Tensor(
        name = name,
        shape = getShape,
        dtype = dtype.flatMap(TypeMapper.toType).getOrElse(DataType.DT_FLOAT),
        profile = None
      )
  }
}
