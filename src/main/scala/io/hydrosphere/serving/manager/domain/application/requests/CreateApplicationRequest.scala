package io.hydrosphere.serving.manager.domain.application.requests

import io.hydrosphere.serving.manager.domain.application.Application

case class CreateApplicationRequest(
  name: String,
  namespace: Option[String],
  executionGraph: ExecutionGraphRequest,
  kafkaStreaming: Option[List[Application.KafkaParams]],
  metadata: Option[Map[String, String]]
)

object CreateApplicationRequest {
  import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol._
  implicit val format = jsonFormat5(CreateApplicationRequest.apply)
}