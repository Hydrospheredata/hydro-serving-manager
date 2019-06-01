package io.hydrosphere.serving.manager.discovery.servable

trait ServableDiscoveryHub[F[_]] {
  def added(): F[Unit]
  def removed(): F[Unit]
}
