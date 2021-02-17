package io.hydrosphere.serving.manager.domain.application.migrations

import cats.data.NonEmptyList
import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfiguration
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.contract.Signature

@JsonCodec
case class VersionGraphAdapter(
    stages: NonEmptyList[VersionStage]
)

@JsonCodec
case class VersionStage(
    modelVariants: NonEmptyList[DeploymentModelVariant],
    signature: Signature
)
@JsonCodec
case class DeploymentModelVariant(
    modelVersion: ModelVersion.Internal,
    weight: Int,
    deploymentConfigName: Option[String] = None
)

@JsonCodec
case class ServableGraphAdapter(
    stages: NonEmptyList[ServableStage]
)

@JsonCodec
case class ServableVariant(
    item: String,
    weight: Int,
    deploymentConfig: Option[DeploymentConfiguration] = None
)
@JsonCodec
case class ServableStage(
    modelVariants: NonEmptyList[ServableVariant],
    signature: Signature
)
