package io.hydrosphere.serving.manager.domain.monitoring

import spray.json._

case class MonitoringConfiguration(batch_size: Int = 100) {

}


object MCProtocol extends DefaultJsonProtocol {
  implicit val MCFormat = jsonFormat1(MonitoringConfiguration)
}

object MCDefault {
  val JsValue = """{ "batch_size": 100 }""".parseJson
}