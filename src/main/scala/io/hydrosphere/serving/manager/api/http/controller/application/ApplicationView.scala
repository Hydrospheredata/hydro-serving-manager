package io.hydrosphere.serving.manager.api.http.controller.application

import cats.data.NonEmptyList
import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.api.http.controller.application.ApplicationGraphView.StageView
import io.hydrosphere.serving.manager.domain.application.{
  Application,
  ApplicationGraph,
  ApplicationKafkaStream
}
import io.hydrosphere.serving.manager.domain.contract.Signature

@JsonCodec
case class ApplicationGraphView(
    stages: NonEmptyList[StageView]
)

object ApplicationGraphView {
  @JsonCodec
  case class VariantView(
      modelVersionId: Long,
      servableName: Option[String],
      deploymentConfigurationName: Option[String],
      weight: Int
  )

  @JsonCodec
  case class StageView(
      modelVariants: NonEmptyList[VariantView],
      signature: Signature
  )

  def fromGraph(graph: ApplicationGraph): ApplicationGraphView = {
    val stages = graph.stages.map { s =>
      val variants = s.variants.map { ss =>
        VariantView(
          ss.modelVersion.id,
          ss.servable.map(_.name),
          ss.requiredDeploymentConfig.map(_.name),
          ss.weight
        )
      }
      StageView(variants, s.signature)
    }
    ApplicationGraphView(stages)
  }
}

@JsonCodec
case class ApplicationView(
    id: Long,
    name: String,
    status: String,
    signature: Signature,
    executionGraph: ApplicationGraphView,
    kafkaStreaming: List[ApplicationKafkaStream],
    message: Option[String],
    metadata: Map[String, String]
)

object ApplicationView {
  def fromApplication(app: Application): ApplicationView = {
    val status  = app.status.entryName
    val message = app.statusMessage
    val graph   = ApplicationGraphView.fromGraph(app.graph)

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
