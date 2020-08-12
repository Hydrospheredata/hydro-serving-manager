package io.hydrosphere.serving.manager.domain.monitoring

import spray.json._

case class MonitoringConfiguration(batchSize: Int)

object MonitoringConfiguration {
  val defaultBatchSize: Int = 100
  val defaultJsValue: JsValue = """{ "batch_size": 100 }""".parseJson

  def apply(batchSize: Int = defaultBatchSize): MonitoringConfiguration = {
    new MonitoringConfiguration(batchSize)
  }
}

object MonitoringConfigurationProtocol extends DefaultJsonProtocol {
  implicit val MCFormat = jsonFormat1(MonitoringConfiguration.apply)
}

