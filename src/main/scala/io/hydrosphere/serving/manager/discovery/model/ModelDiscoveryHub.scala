package io.hydrosphere.serving.manager.discovery.model

import io.hydrosphere.serving.manager.grpc.entities.ModelVersion

trait ModelDiscoveryHub[F[_]] {
  def update(modelVersion: ModelVersion): F[Unit]
  def deleted(modelId: Long): F[Unit]
}
