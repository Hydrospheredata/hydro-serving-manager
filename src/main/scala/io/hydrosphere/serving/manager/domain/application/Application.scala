package io.hydrosphere.serving.manager.domain.application

import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.domain.application.graph.ExecutionGraph

case class Application(
  id: Long,
  name: String,
  status: Application.Status,
  kafkaStreaming: List[Application.KafkaParams],
  graph: ExecutionGraph,
  metadata: Map[String, String] = Map.empty
)

object Application {

  sealed trait Status extends Product with Serializable

  case class Unhealthy(reason: Option[String]) extends Status

  case object Healthy extends Status

  case class KafkaParams(
    sourceTopic: String,
    destinationTopic: String,
    consumerId: Option[String],
    errorTopic: Option[String]
  )

}