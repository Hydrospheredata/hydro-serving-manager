package io.hydrosphere.serving.manager.api.http.controller.model

import io.circe.generic.JsonCodec
import io.hydrosphere.serving.contract.model_contract.ModelContract

@JsonCodec
case class RegisterModelRequest(
  name: String,
  contract: ModelContract,
  metadata: Option[Map[String, String]] = None,
)
