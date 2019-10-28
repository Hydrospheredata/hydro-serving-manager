package io.hydrosphere.serving.manager.domain.monitoring

sealed trait ThresholdCmpOperator

object ThresholdCmpOperator {

  case object Eq extends ThresholdCmpOperator

  case object NotEq extends ThresholdCmpOperator

  case object Greater extends ThresholdCmpOperator

  case object Less extends ThresholdCmpOperator

  case object GreaterEq extends ThresholdCmpOperator

  case object LessEq extends ThresholdCmpOperator

}