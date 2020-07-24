package io.hydrosphere.serving.manager.api.http.controller.model

import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.domain.monitoring.MonitoringConfiguration

case class RegisterModelRequest(
  name: String,
  contract: ModelContract,
  metadata: Option[Map[String, String]] = None,
  monitoringConfiguration: MonitoringConfiguration
)
