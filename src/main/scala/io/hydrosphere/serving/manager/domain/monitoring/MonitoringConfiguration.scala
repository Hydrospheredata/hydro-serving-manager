package io.hydrosphere.serving.manager.domain.monitoring

import spray.json._


object MonitoringConfiguration {
  val defaultValue: JsValue = """{ "batch_size": "100" }""".parseJson
}