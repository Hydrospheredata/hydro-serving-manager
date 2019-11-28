package io.hydrosphere.serving.manager.api.http.controller.model

import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.domain.image.DockerImage

case class RegisterModelRequest(
  name: String,
  image: DockerImage,
  contract: ModelContract,
  metadata: Option[Map[String, String]] = None
)
