package io.hydrosphere.serving.manager.domain.application.migrations

import cats.data.NonEmptyList
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfiguration
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol._


case class VersionGraphAdapter(
  stages: NonEmptyList[VersionStage]
)
object VersionGraphAdapter {
  implicit val jsVVV = jsonFormat3(DeploymentModelVariant.apply)
  implicit val vsVV = jsonFormat2(VersionStage.apply)
  implicit val jsV = jsonFormat1(VersionGraphAdapter.apply)
}
case class VersionStage(
  modelVariants: NonEmptyList[DeploymentModelVariant],
  signature: ModelSignature
)
case class DeploymentModelVariant(
  modelVersion: ModelVersion.Internal,
  weight: Int,
  deploymentConfigName: Option[String] = None
)

case class ServableGraphAdapter(
  stages: NonEmptyList[ServableStage],
)
object ServableGraphAdapter {
  implicit val svar = jsonFormat3(ServableVariant.apply)
  implicit val servableStageFormat = jsonFormat2(ServableStage.apply)
  implicit val jsS = jsonFormat1(ServableGraphAdapter.apply)
}
case class ServableVariant(
  item: String,
  weight: Int,
  deploymentConfig: Option[DeploymentConfiguration] = None,
)
case class ServableStage(
  modelVariants: NonEmptyList[ServableVariant],
  signature: ModelSignature
)