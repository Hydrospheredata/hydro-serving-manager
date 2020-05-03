package io.hydrosphere.serving.manager.api.http.controller.model

import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.domain.contract.Contract
import io.hydrosphere.serving.manager.domain.image.DockerImage

@JsonCodec
case class ModelUploadMetadata(
  name: String,
  runtime: DockerImage,
  hostSelectorName: Option[String] = None,
  contract: Option[Contract] = None,
  installCommand: Option[String] = None,
  metadata: Option[Map[String, String]] = None
)