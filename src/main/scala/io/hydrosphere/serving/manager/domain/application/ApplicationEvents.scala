package io.hydrosphere.serving.manager.domain.application

import io.hydrosphere.serving.manager.discovery.DiscoveryService
import io.hydrosphere.serving.manager.domain.application.Application.GenericApplication

object ApplicationEvents extends DiscoveryService[GenericApplication, String]