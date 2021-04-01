package io.hydrosphere.serving.manager.config

import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.domain.deploy_config._

/**
  * Used only to define a deployment configuration in manager's config files.
  * Name is hardcoded as [[DEFAULT_DEPLOYMENT_CONFIG_NAME]]
  *
  * @param container
  * @param pod
  * @param deployment
  * @param hpa
  */
@JsonCodec
final case class DefaultDeploymentConfiguration(
    container: Option[K8sContainerConfig],
    pod: Option[K8sPodConfig],
    deployment: Option[K8sDeploymentConfig],
    hpa: Option[K8sHorizontalPodAutoscalerConfig]
) {
  def toDC: DeploymentConfiguration =
    DeploymentConfiguration(
      name = DEFAULT_DEPLOYMENT_CONFIG_NAME,
      container = container,
      pod = pod,
      deployment = deployment,
      hpa = hpa
    )
}
