package io.hydrosphere.serving.manager.domain.monitoring

import io.circe.generic.JsonCodec

import java.util.UUID
import io.hydrosphere.serving.manager.domain.servable.Servable

@JsonCodec
case class CustomModelMetricSpecConfiguration(
  modelVersionId: Long,
  threshold: Double,
  thresholdCmpOperator: ThresholdCmpOperator,
  servable: Option[Servable],
  deploymentConfigName: Option[String]
)

@JsonCodec
case class CustomModelMetricSpec(
  name: String,
  modelVersionId: Long,
  config: CustomModelMetricSpecConfiguration,
  id: String = UUID.randomUUID().toString
)