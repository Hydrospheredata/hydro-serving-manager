package io.hydrosphere.serving.manager.api.http.controller.application

import cats.data.NonEmptyList
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.api.http.controller.application.ApplicationGraphView.StageView
import io.hydrosphere.serving.manager.domain.application.{Application, ApplicationGraph, ApplicationKafkaStream}
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol._

case class ApplicationGraphView(
  stages: NonEmptyList[StageView]
)

object ApplicationGraphView {
  case class VariantView(
    modelVersion: ModelVersion.Internal,
    weight: Int
  )
  object VariantView {
    implicit val jsformat = jsonFormat2(VariantView.apply)
  }
  case class StageView(
    modelVariants: NonEmptyList[VariantView],
    signature: ModelSignature
  )
  object StageView {
    implicit val jsformat = jsonFormat2(StageView.apply)
  }

  implicit val format = jsonFormat1(ApplicationGraphView.apply)

  def fromGraph(graph: ApplicationGraph): ApplicationGraphView = {
    val stages = graph.stages.map{ s =>
      val variants = s.variants.map{ss =>
        VariantView(ss.modelVersion, ss.weight)
      }
      StageView(variants, s.signature)
    }
    ApplicationGraphView(stages)
  }
}

case class ApplicationView(
  id: Long,
  name: String,
  status: String,
  signature: ModelSignature,
  executionGraph: ApplicationGraphView,
  kafkaStreaming: List[ApplicationKafkaStream],
  message: Option[String],
  metadata: Map[String, String]
)

object ApplicationView {
  def fromApplication(app: Application): ApplicationView = {
    val status = app.status.productPrefix
    val message = app.statusMessage
    val graph = ApplicationGraphView.fromGraph(app.graph)
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
