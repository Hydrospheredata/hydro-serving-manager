package io.hydrosphere.serving.manager.domain.application.requests

import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.domain.application.ApplicationKafkaStream

@JsonCodec
case class UpdateApplicationRequest(
  id: Long,
  name: String,
  namespace: Option[String],
  executionGraph: ExecutionGraphRequest,
  kafkaStreaming: Option[List[ApplicationKafkaStream]],
  metadata: Option[Map[String, String]]
)
