package io.hydrosphere.serving.manager.api.http.controller.model

import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.domain.monitoring.MonitoringConfiguration

@JsonCodec
case class RegisterModelRequest(
    name: String,
    signature: Signature,
    metadata: Option[Map[String, String]] = None,
    monitoringConfiguration: Option[MonitoringConfiguration]
)
