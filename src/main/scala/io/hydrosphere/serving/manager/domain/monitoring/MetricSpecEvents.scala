package io.hydrosphere.serving.manager.domain.monitoring

import io.hydrosphere.serving.manager.discovery.DiscoveryService

object MetricSpecEvents extends DiscoveryService[CustomModelMetricSpec, String] {}