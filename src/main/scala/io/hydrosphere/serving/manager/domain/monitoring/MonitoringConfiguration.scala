package io.hydrosphere.serving.manager.domain.monitoring

import io.hydrosphere.serving.manager.grpc
import io.hydrosphere.serving.manager.grpc.entities.{MonitoringConfiguration => GMonitoringConfiguration}
import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol._

import spray.json._

object BatchSize {
  val defaultValue: Int = 100
}

case class MonitoringConfiguration(
  batch_size: Int = BatchSize.defaultValue
) {
  def toGrpc: GMonitoringConfiguration = grpc.entities.MonitoringConfiguration(this.batch_size)
}

object MonitoringConfiguration {
  implicit val js1: RootJsonFormat[MonitoringConfiguration] = jsonFormat1(MonitoringConfiguration.apply)
}
