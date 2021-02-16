package io.hydrosphere.serving.manager.domain.model_version

import io.circe.generic.JsonCodec

import java.time.Instant
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.monitoring.MonitoringConfiguration

@JsonCodec
sealed trait ModelVersion extends Product with Serializable {
  def id: Long
  def modelSignature: Signature
  def modelVersion: Long
  def model: Model
  def metadata: Map[String, String]
  def fullName: String
}

object ModelVersion {

  @JsonCodec
  case class Internal(
      id: Long,
      image: DockerImage,
      created: Instant,
      finished: Option[Instant],
      modelVersion: Long,
      modelSignature: Signature,
      runtime: DockerImage,
      model: Model,
      status: ModelVersionStatus,
      installCommand: Option[String],
      metadata: Map[String, String],
      monitoringConfiguration: MonitoringConfiguration = MonitoringConfiguration()
  ) extends ModelVersion {
    def fullName: String = s"${model.name}:$modelVersion"
  }

  @JsonCodec
  case class External(
      id: Long,
      created: Instant,
      modelVersion: Long,
      modelSignature: Signature,
      model: Model,
      metadata: Map[String, String],
      monitoringConfiguration: MonitoringConfiguration = MonitoringConfiguration()
  ) extends ModelVersion {
    def fullName: String = s"${model.name}:$modelVersion"
  }
}
