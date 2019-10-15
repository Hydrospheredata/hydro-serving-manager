package io.hydrosphere.serving.manager.api.http.controller.application

import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.domain.application.Application
import io.hydrosphere.serving.manager.domain.application.graph.compat.VersionGraphAdapter

case class ApplicationView(
  id: Long,
  name: String,
  status: String,
  signature: ModelSignature,
  executionGraph: VersionGraphAdapter,
  kafkaStreaming: List[Application.KafkaParams],
  message: Option[String],
  metadata: Map[String, String]
)

object ApplicationView {
  def fromApplication(app: Application): ApplicationView = {
    val message = app.status match {
      case Application.Unhealthy(reason) =>
        reason
      case Application.Healthy =>
        None
    }
    ApplicationView(
      id = app.id,
      name = app.name,
      status = app.status.productPrefix,
      signature = app.graph.signature,
      executionGraph = ???, // TODO
      kafkaStreaming = app.kafkaStreaming,
      message = message,
      metadata = app.metadata
    )
  }
}