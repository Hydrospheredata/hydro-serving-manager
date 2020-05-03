package io.hydrosphere.serving.manager.domain.model

import io.circe.generic.JsonCodec
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.api.http.controller.model.ModelUploadMetadata
import io.hydrosphere.serving.manager.domain.contract.Contract
import io.hydrosphere.serving.manager.domain.host_selector.HostSelector
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.infrastructure.storage.fetchers.FetcherResult

@JsonCodec
case class ModelVersionMetadata(
    modelName: String,
    contract: Contract,
    runtime: DockerImage,
    hostSelector: Option[HostSelector],
    installCommand: Option[String],
    metadata: Map[String, String]
)

object ModelVersionMetadata {
  def combineMetadata(
      fetcherResult: Option[FetcherResult],
      upload: ModelUploadMetadata,
      hs: Option[HostSelector]
  ): Option[ModelVersionMetadata] = {
    for {
      contract <- upload.contract
        .orElse(fetcherResult.map(_.modelContract))
      metadata = fetcherResult.map(_.metadata).getOrElse(Map.empty) ++ upload.metadata.getOrElse(
        Map.empty
      )
    } yield ModelVersionMetadata(
      modelName = upload.name,
      contract = contract,
      runtime = upload.runtime,
      hostSelector = hs,
      installCommand = upload.installCommand,
      metadata = metadata
    )
  }

}
