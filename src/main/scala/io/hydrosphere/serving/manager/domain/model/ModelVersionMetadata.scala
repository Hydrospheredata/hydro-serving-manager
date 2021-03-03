package io.hydrosphere.serving.manager.domain.model

import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.api.http.controller.model.ModelUploadMetadata
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.monitoring.MonitoringConfiguration
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.FetcherResult

@JsonCodec
case class ModelVersionMetadata(
    modelName: String,
    signature: Signature,
    runtime: DockerImage,
    installCommand: Option[String],
    metadata: Map[String, String],
    monitoringConfiguration: MonitoringConfiguration = MonitoringConfiguration()
)

object ModelVersionMetadata {
  def combineMetadata(
      fetcherResult: Option[FetcherResult],
      upload: ModelUploadMetadata
  ): Option[ModelVersionMetadata] =
    for {
      signature <- upload.modelSignature.orElse(fetcherResult.map(_.modelSignature))
      metadata =
        fetcherResult.map(_.metadata).getOrElse(Map.empty) ++ upload.metadata.getOrElse(Map.empty)
      monitoringConfiguration = upload.monitoringConfiguration.getOrElse(MonitoringConfiguration())
    } yield ModelVersionMetadata(
      modelName = upload.name,
      signature = signature,
      runtime = upload.runtime,
      installCommand = upload.installCommand,
      metadata = metadata,
      monitoringConfiguration = monitoringConfiguration
    )
}
