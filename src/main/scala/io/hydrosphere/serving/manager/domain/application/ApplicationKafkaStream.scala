package io.hydrosphere.serving.manager.domain.application

import io.circe.generic.JsonCodec

@JsonCodec
case class ApplicationKafkaStream(
  sourceTopic: String,
  destinationTopic: String,
  consumerId: Option[String],
  errorTopic: Option[String]
)
