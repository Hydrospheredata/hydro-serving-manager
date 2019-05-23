package io.hydrosphere.serving.manager.discovery.servable

trait ServableDiscoveryHub[F[_]] {
  def added()
  def removed()
}
