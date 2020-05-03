package io.hydrosphere.serving.manager.infrastructure.storage.fetchers.keras

import cats.data.NonEmptyList
import io.circe.Json
import io.circe.generic.JsonCodec
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}
import io.hydrosphere.serving.manager.domain.contract.{DataType, Field, Signature, TensorShape}
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.tensorflow.TypeMapper

@ConfiguredJsonCodec
private[keras] sealed trait ModelConfig {
  def toPredictSignature: Option[Signature]
}

private[keras] object ModelConfig {

  @JsonCodec
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

      for {
        inputs  <- NonEmptyList.fromList(inputLayers.flatMap(_.config.field))
        outputs <- NonEmptyList.fromList(outputLayers.flatMap(_.config.field))
      } yield Signature(
        signatureName = "Predict",
        inputs = inputs,
        outputs = outputs
      )
    }
  }

  @JsonCodec
  case class FunctionalModelConfig(
      name: String,
      layers: List[FunctionalLayerConfig],
      inputLayers: List[List[Json]],
      outputLayers: List[List[Json]]
  )

  @JsonCodec
  case class FunctionalLayerConfig(
      name: String,
      className: String,
      config: LayerConfig,
      inboundNodes: Json
  )

  @JsonCodec
  case class Sequential(config: List[SequentialLayerConfig]) extends ModelConfig {
    override def toPredictSignature: Option[Signature] = {
      for {
        firstLayer <- config.headOption
        lastLayer  <- config.lastOption
        input      <- firstLayer.config.field
        output     <- lastLayer.config.field
      } yield Signature(
        signatureName = "Predict",
        inputs = NonEmptyList.of(input),
        outputs = NonEmptyList.of(output)
      )
    }
  }

  @JsonCodec
  case class SequentialLayerConfig(className: String, config: LayerConfig)

  @JsonCodec
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

    def field: Option[Field] = {
      for {
        dtype <- dtype.flatMap(TypeMapper.toType)
      } yield Field.Tensor(
        name = name,
        shape = getShape,
        dtype = dtype,
        profile = None
      )
    }
  }

  implicit val config = Configuration.default.withDiscriminator("class_name")

}
