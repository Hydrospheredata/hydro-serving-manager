package io.hydrosphere.serving.manager.domain.model_version

import io.circe.generic.JsonCodec

import java.time.Instant

import io.hydrosphere.serving.manager.domain.application.Application
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.monitoring.MonitoringConfiguration

@JsonCodec
case class ModelVersionView(
    id: Long,
    created: Instant,
    finished: Option[Instant],
    modelVersion: Long,
    modelSignature: Signature,
    model: Model,
    status: String,
    metadata: Map[String, String],
    applications: List[String],
    image: Option[DockerImage],
    runtime: Option[DockerImage],
    monitoringConfiguration: MonitoringConfiguration,
    isExternal: Boolean
)

object ModelVersionView {
  def fromVersion(amv: ModelVersion, applications: List[Application]): ModelVersionView =
    amv match {
      case internalMV: ModelVersion.Internal =>
        ModelVersionView(
          id = internalMV.id,
          image = Some(internalMV.image),
          created = internalMV.created,
          finished = internalMV.finished,
          modelVersion = internalMV.modelVersion,
          modelSignature = internalMV.modelSignature,
          runtime = Some(internalMV.runtime),
          model = internalMV.model,
          status = internalMV.status.toString,
          applications = applications.map(_.name),
          metadata = internalMV.metadata,
          isExternal = false,
          monitoringConfiguration = internalMV.monitoringConfiguration
        )
      case externalMV: ModelVersion.External =>
        ModelVersionView(
          id = externalMV.id,
          image = None,
          created = externalMV.created,
          finished = Some(externalMV.created),
          modelVersion = externalMV.modelVersion,
          modelSignature = externalMV.modelSignature,
          runtime = None,
          model = externalMV.model,
          status = ModelVersionStatus.Released.toString,
          applications = Nil,
          metadata = externalMV.metadata,
          isExternal = true,
          monitoringConfiguration = externalMV.monitoringConfiguration
        )
    }
}
