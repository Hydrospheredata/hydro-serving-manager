package io.hydrosphere.serving.manager.domain.model

import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.contract.model_field.ModelField
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.api.http.controller.model.ModelUploadMetadata
import io.hydrosphere.serving.manager.data_profile_types.DataProfileType
import io.hydrosphere.serving.manager.domain.DomainError.InvalidRequest
import io.hydrosphere.serving.manager.domain.host_selector.HostSelector
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.monitoring.MonitoringConfiguration
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.FetcherResult

case class ModelVersionMetadata(
  modelName: String,
  contract: ModelContract,
  runtime: DockerImage,
  hostSelector: Option[HostSelector],
  installCommand: Option[String],
  metadata: Map[String, String],
  monitoringConfiguration: MonitoringConfiguration
)

object ModelVersionMetadata {
  def combineMetadata(fetcherResult: Option[FetcherResult], upload: ModelUploadMetadata, hs: Option[HostSelector]): ModelVersionMetadata = {
    val contract = upload.contract
      .orElse(fetcherResult.map(_.modelContract))
      .getOrElse(ModelContract.defaultInstance)

    val metadata = fetcherResult.map(_.metadata).getOrElse(Map.empty) ++ upload.metadata.getOrElse(Map.empty)

    ModelVersionMetadata(
      modelName = upload.name,
      contract = contract,
      runtime = upload.runtime,
      hostSelector = hs,
      installCommand = upload.installCommand,
      metadata = metadata,
      monitoringConfiguration = upload.monitoringConfiguration
    )
  }

}