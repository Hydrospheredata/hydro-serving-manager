package io.hydrosphere.serving.manager.domain.application.requests

import io.hydrosphere.serving.manager.domain.application.Application


case class UpdateApplicationRequest(
  id: Long,
  name: String,
  namespace: Option[String],
  executionGraph: ExecutionGraphRequest,
  kafkaStreaming: Option[List[Application.KafkaParams]],
  metadata: Option[Map[String, String]]
)

object UpdateApplicationRequest {

  import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol._

  implicit val format = jsonFormat6(UpdateApplicationRequest.apply)
}