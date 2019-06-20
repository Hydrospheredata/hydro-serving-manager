package io.hydrosphere.serving.manager.discovery

sealed trait DiscoveryEvent[+T, +K]
object DiscoveryEvent {

  case object Initial extends DiscoveryEvent[Nothing, Nothing]

  case class ItemUpdate[T, K](items: List[T]) extends DiscoveryEvent[T, K]

  case class ItemRemove[T, K](items: List[K]) extends DiscoveryEvent[T, K]

}