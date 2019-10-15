package io.hydrosphere.serving.manager.domain.model_version

import java.time.Instant

import io.hydrosphere.serving.manager.domain.Contract
import io.hydrosphere.serving.manager.domain.host_selector.HostSelector
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.ModelVersionStatus.ModelVersionStatus

case class ModelVersion(
  id: Long,
  image: DockerImage,
  created: Instant,
  finished: Option[Instant],
  modelVersion: Long,
  contract: Contract,
  runtime: DockerImage,
  model: Model,
  hostSelector: Option[HostSelector],
  status: ModelVersionStatus,
  installCommand: Option[String],
  metadata: Map[String, String]
) {
  def fullName: String = s"${model.name}:$modelVersion"
}