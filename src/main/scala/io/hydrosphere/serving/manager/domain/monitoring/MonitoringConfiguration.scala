package io.hydrosphere.serving.manager.domain.monitoring

import spray.json._
import DefaultJsonProtocol._

case class MonitoringConfiguration(batchSize: Int)

object MonitoringConfiguration {
  val defaultBatchSize: Int = 100

  def apply(batchSize: Int = defaultBatchSize): MonitoringConfiguration = {
    new MonitoringConfiguration(batchSize)
  }

  implicit val JSONFormat: RootJsonFormat[MonitoringConfiguration] = jsonFormat1(MonitoringConfiguration.apply)
}
