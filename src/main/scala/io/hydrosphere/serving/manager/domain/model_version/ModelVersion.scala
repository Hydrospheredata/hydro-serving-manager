package io.hydrosphere.serving.manager.domain.model_version

import java.time.Instant

import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.domain.host_selector.HostSelector
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.ModelVersionStatus.ModelVersionStatus
import io.hydrosphere.serving.manager.data_profile_types.DataProfileType
import java.time.LocalDateTime

case class ModelVersion(
  id: Long,
  image: DockerImage,
  created: Instant,
  finished: Option[Instant],
  modelVersion: Long,
  modelContract: ModelContract,
  runtime: DockerImage,
  model: Model,
  hostSelector: Option[HostSelector],
  status: ModelVersionStatus,
  installCommand: Option[String],
  metadata: Map[String, String],
  isExternal: Boolean
) {
  def fullName: String = s"${model.name}:$modelVersion"
}