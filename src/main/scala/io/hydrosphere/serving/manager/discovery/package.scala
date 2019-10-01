package io.hydrosphere.serving.manager

import io.hydrosphere.serving.manager.domain.application.Application
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.Servable

package object discovery {
  type ApplicationPublisher[F[_]] = DiscoveryPublisher[F, Application, String]
  type ApplicationSubscriber[F[_]] = DiscoverySubscriber[F, Application, String]

  type ServablePublisher[F[_]] = DiscoveryPublisher[F, Servable, String]
  type ServableSubscriber[F[_]] = DiscoverySubscriber[F, Servable, String]

  type ModelPublisher[F[_]] = DiscoveryPublisher[F, ModelVersion, Long]
  type ModelSubscriber[F[_]] = DiscoverySubscriber[F, ModelVersion, Long]
}
