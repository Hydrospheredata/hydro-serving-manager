package io.hydrosphere.serving.manager.util

case class BiMap[K, V] private (forward: Map[K, V], backward: Map[V, K])

object BiMap {
  def apply[K, V](tuples: (K, V)*): BiMap[K, V] = {
    val forward  = Map.from(tuples)
    val backward = forward.map { case (k, v) => v -> k }
    new BiMap(forward, backward)
  }
}
