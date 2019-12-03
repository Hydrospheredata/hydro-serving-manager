package io.hydrosphere.serving.manager.api.http.controller.model

import io.hydrosphere.serving.contract.model_contract.ModelContract

case class RegisterModelRequest(
  name: String,
  contract: ModelContract,
  metadata: Option[Map[String, String]] = None,
)
