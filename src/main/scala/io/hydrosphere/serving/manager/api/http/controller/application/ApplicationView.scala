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
import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfiguration
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableView}

@JsonCodec
case class ApplicationGraphView(
    stages: NonEmptyList[StageView]
)

object ApplicationGraphView {
  @JsonCodec
  case class VariantView(
      modelVersionId: Long,
      servable: Option[ServableView],
      deploymentConfiguration: Option[DeploymentConfiguration],
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
          ss.servable.map(ServableView.fromServable),
          ss.requiredDeploymentConfig,
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
