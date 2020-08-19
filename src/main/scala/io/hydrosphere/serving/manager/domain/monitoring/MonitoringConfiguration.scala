package io.hydrosphere.serving.manager.domain.monitoring

import spray.json._
import DefaultJsonProtocol._

case class MonitoringConfiguration(batchSize: Int = 100)

object MonitoringConfiguration {
  val defaultBatchSize: Int = 100

  implicit val JSONFormat: RootJsonFormat[MonitoringConfiguration] = jsonFormat1(MonitoringConfiguration.apply)
}
