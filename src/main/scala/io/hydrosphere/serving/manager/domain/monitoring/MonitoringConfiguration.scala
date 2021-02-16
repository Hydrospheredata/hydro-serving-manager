package io.hydrosphere.serving.manager.domain.monitoring

import io.circe.generic.JsonCodec

@JsonCodec
case class MonitoringConfiguration(batchSize: Int = 100)
