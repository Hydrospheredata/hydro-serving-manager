package io.hydrosphere.serving.manager.domain.application.requests

import io.hydrosphere.serving.manager.domain.application.ApplicationKafkaStream

case class UpdateApplicationRequest(
  id: Long,
  name: String,
  namespace: Option[String],
  executionGraph: ExecutionGraphRequest,
  kafkaStreaming: Option[List[ApplicationKafkaStream]],
  metadata: Option[Map[String, String]]
)