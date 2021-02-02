package io.hydrosphere.serving.manager.domain.monitoring

import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}

@ConfiguredJsonCodec
sealed trait ThresholdCmpOperator

object ThresholdCmpOperator {
  implicit val configuration: Configuration = Configuration.default.withDiscriminator("kind")

  case object Eq extends ThresholdCmpOperator

  case object NotEq extends ThresholdCmpOperator

  case object Greater extends ThresholdCmpOperator

  case object Less extends ThresholdCmpOperator

  case object GreaterEq extends ThresholdCmpOperator

  case object LessEq extends ThresholdCmpOperator

}