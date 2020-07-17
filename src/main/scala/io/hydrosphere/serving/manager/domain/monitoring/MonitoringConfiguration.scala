package io.hydrosphere.serving.manager.domain.monitoring

import io.hydrosphere.serving.manager.grpc.entities.{MonitoringConfiguration => GMonitoringConfiguration}

case class MonitoringConfiguration(
  batch_size: Int
) {
  def toGrpc = GMonitoringConfiguration(
    batch_size = batch_size
  )
}


