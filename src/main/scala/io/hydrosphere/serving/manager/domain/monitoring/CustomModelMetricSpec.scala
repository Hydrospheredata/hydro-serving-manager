package io.hydrosphere.serving.manager.domain.monitoring

import java.util.UUID

import io.hydrosphere.serving.manager.domain.servable.Servable.GenericServable


case class CustomModelMetricSpecConfiguration(
  modelVersionId: Long,
  threshold: Option[Double],
  thresholdCmpOperator: Option[ThresholdCmpOperator],
  servable: Option[GenericServable]
)

case class CustomModelMetricSpec(
  name: String,
  modelVersionId: Long,
  config: CustomModelMetricSpecConfiguration,
  id: String = UUID.randomUUID().toString
)
