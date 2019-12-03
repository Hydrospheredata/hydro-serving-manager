package io.hydrosphere.serving.manager.domain.model_version

import java.time.Instant

import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.domain.host_selector.HostSelector
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.ModelVersionStatus.ModelVersionStatus
import io.hydrosphere.serving.manager.data_profile_types.DataProfileType
import java.time.LocalDateTime

sealed trait ModelVersion extends Product with Serializable {
  def id: Long
  def modelContract: ModelContract
  def modelVersion: Long
  def model: Model
  def metadata: Map[String, String]
  def fullName: String
}

object ModelVersion {

  case class Internal(
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
    metadata: Map[String, String]
  ) extends ModelVersion {
    def fullName: String = s"${model.name}:$modelVersion"
  }

  case class External(
    id: Long,
    created: Instant,
    modelVersion: Long,
    modelContract: ModelContract,
    model: Model,
    metadata: Map[String, String],
  ) extends ModelVersion {
    def fullName: String = s"${model.name}:$modelVersion"
  }

}