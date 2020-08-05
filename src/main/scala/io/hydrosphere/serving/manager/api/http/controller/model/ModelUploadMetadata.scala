package io.hydrosphere.serving.manager.api.http.controller.model

import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.data_profile_types.DataProfileType
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.monitoring.MonitoringConfiguration

case class ModelUploadMetadata(
  name: String,
  runtime: DockerImage,
  hostSelectorName: Option[String] = None,
  contract: Option[ModelContract] = None,
  profileTypes: Option[Map[String, DataProfileType]] = None,
  installCommand: Option[String] = None,
  metadata: Option[Map[String, String]] = None,
  monitoringConfiguration: Option[MonitoringConfiguration]
)