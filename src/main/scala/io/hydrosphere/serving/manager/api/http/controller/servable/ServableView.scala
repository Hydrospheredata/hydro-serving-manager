package io.hydrosphere.serving.manager.api.http.controller.servable

import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.hydrosphere.serving.manager.domain.servable.Servable.GenericServable
import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol

case class ServableView(
  modelVersion: ModelVersion.Internal,
  status: Servable.Status,
  fullName: String,
  metadata: Map[String, String]
)

object ServableView extends CompleteJsonProtocol {

  implicit val format = jsonFormat4(ServableView.apply)

  def fromServable(s: GenericServable) = {
    ServableView(
      modelVersion = s.modelVersion,
      status = s.status,
      fullName = s.fullName,
      metadata = s.metadata
    )
  }
}