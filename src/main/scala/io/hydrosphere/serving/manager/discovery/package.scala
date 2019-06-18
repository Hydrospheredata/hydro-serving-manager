package io.hydrosphere.serving.manager

import io.hydrosphere.serving.manager.domain.application.Application.GenericApplication
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.Servable.GenericServable

package object discovery {
  type ApplicationPublisher[F[_]] = DiscoveryPublisher[F, GenericApplication, Long]
  type ApplicationSubscriber[F[_]] = DiscoverySubscriber[F, GenericApplication, Long]

  type ServablePublisher[F[_]] = DiscoveryPublisher[F, GenericServable, String]
  type ServableSubscriber[F[_]] = DiscoverySubscriber[F, GenericServable, String]

  type ModelPublisher[F[_]] = DiscoveryPublisher[F, ModelVersion, Long]
  type ModelSubscriber[F[_]] = DiscoverySubscriber[F, ModelVersion, Long]
}
