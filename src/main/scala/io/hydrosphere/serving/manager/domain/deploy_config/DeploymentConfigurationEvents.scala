package io.hydrosphere.serving.manager.domain.deploy_config

import io.hydrosphere.serving.manager.discovery.DiscoveryService

object DeploymentConfigurationEvents extends DiscoveryService[DeploymentConfiguration, DeploymentConfigurationName]

