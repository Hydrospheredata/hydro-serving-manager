package io.hydrosphere.serving.manager.domain.servable

import io.hydrosphere.serving.manager.discovery.DiscoveryService
import io.hydrosphere.serving.manager.domain.servable.Servable.GenericServable

object ServableEvents extends DiscoveryService[GenericServable, String]