package io.hydrosphere.serving.manager.domain.monitoring

import io.hydrosphere.serving.manager.grpc
import io.hydrosphere.serving.manager.grpc.entities.{MonitoringConfiguration => GMonitoringConfiguration}
import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol._

import spray.json._

case class MonitoringConfiguration(
  batch_size: Int
) {
  def toGrpc: GMonitoringConfiguration = grpc.entities.MonitoringConfiguration(this.batch_size)
}

object MonitoringConfiguration {
  implicit val js1: RootJsonFormat[MonitoringConfiguration] = jsonFormat1(MonitoringConfiguration.apply)
}

