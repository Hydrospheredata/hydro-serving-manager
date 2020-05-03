package io.hydrosphere.serving.manager.api.http.controller.model

import io.circe.generic.JsonCodec
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.domain.contract.Contract

@JsonCodec
case class RegisterModelRequest(
    name: String,
    contract: Contract,
    metadata: Option[Map[String, String]] = None
)
