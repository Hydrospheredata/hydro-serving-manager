package io.hydrosphere.serving.manager.api.http.controller.model

import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.domain.contract.DataProfileType
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.monitoring.MonitoringConfiguration

@JsonCodec
case class ModelUploadMetadata(
    name: String,
    runtime: DockerImage,
    hostSelectorName: Option[String] = None,
    modelSignature: Option[Signature] = None,
    profileTypes: Option[Map[String, DataProfileType]] = None,
    installCommand: Option[String] = None,
    metadata: Option[Map[String, String]] = None,
    monitoringConfiguration: Option[MonitoringConfiguration] = None
)
