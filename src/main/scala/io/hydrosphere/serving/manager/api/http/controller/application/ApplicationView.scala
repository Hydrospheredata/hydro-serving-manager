package io.hydrosphere.serving.manager.api.http.controller.application

import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.domain.application.Application.GenericApplication
import io.hydrosphere.serving.manager.domain.application.graph.VersionGraphComposer.PipelineStage
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
  def fromApplication(app: GenericApplication): ApplicationView = {
    val (status, graph, message) = app.status match {
      case Application.Assembling =>
        val graph  = ExecutionGraphAdapter.fromVersionPipeline(app.versionGraph)
        val status = "Assembling"
        (status, graph, None)
      case Application.Failed(reason) =>
        val graph  = ExecutionGraphAdapter.fromVersionPipeline(app.versionGraph)
        val status = "Failed"
        (status, graph, reason)
      case Application.Ready(stages) =>
        val versionGraph = stages.map { node =>
          val signature = node.signature
          val subs = node.variants.map { variant =>
            Variant(variant.item.modelVersion, variant.weight)
          }
          PipelineStage(subs, signature)
        }
        val graph  = ExecutionGraphAdapter.fromVersionPipeline(versionGraph)
        val status = "Ready"
        (status, graph, None)
    }
    ApplicationView(
      id = app.id,
      name = app.name,
      status = status,
      signature = app.signature,
      executionGraph = graph,
      kafkaStreaming = app.kafkaStreaming,
      message = message,
      metadata = app.metadata
    )
  }
}
