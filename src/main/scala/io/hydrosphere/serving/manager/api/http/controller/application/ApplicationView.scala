package io.hydrosphere.serving.manager.api.http.controller.application

import cats.implicits._
import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.domain.application.graph._
import io.hydrosphere.serving.manager.domain.application.{Application, ApplicationKafkaStream}
import io.hydrosphere.serving.manager.domain.contract.Signature

@JsonCodec
case class ApplicationView(
    id: Long,
    name: String,
    status: String,
    signature: Signature,
    executionGraph: VersionGraphAdapter,
    kafkaStreaming: List[ApplicationKafkaStream],
    message: Option[String],
    metadata: Map[String, String]
)

object ApplicationView {
  def fromApplication(app: Application): ApplicationView = {
    val graph = ExecutionGraphAdapter.fromVersionPipeline(app.executionGraph)

    ApplicationView(
      id = app.id,
      name = app.name,
      status = app.status.entryName,
      signature = app.executionGraph.signature,
      executionGraph = graph,
      kafkaStreaming = app.kafkaStreaming,
      message = app.message.some,
      metadata = app.metadata
    )
  }
}
