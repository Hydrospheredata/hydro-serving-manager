package io.hydrosphere.serving.manager.domain.model_version

import java.time.Instant

import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.domain.contract.Contract
import io.hydrosphere.serving.manager.domain.host_selector.HostSelector
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model

@JsonCodec
sealed trait ModelVersion extends Product with Serializable {
  def id: Long
  def modelContract: Contract
  def modelVersion: Long
  def model: Model
  def metadata: Map[String, String]
  def fullName: String = s"${model.name}:$modelVersion"
}

object ModelVersion {

  @JsonCodec
  case class Internal(
      id: Long,
      image: DockerImage,
      created: Instant,
      finished: Option[Instant],
      modelVersion: Long,
      modelContract: Contract,
      runtime: DockerImage,
      model: Model,
      hostSelector: Option[HostSelector],
      status: ModelVersionStatus,
      installCommand: Option[String],
      metadata: Map[String, String]
  ) extends ModelVersion

  @JsonCodec
  case class External(
      id: Long,
      created: Instant,
      modelVersion: Long,
      modelContract: Contract,
      model: Model,
      metadata: Map[String, String]
  ) extends ModelVersion

}
