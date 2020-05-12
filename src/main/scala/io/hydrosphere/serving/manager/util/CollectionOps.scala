package io.hydrosphere.serving.manager.util

object CollectionOps {

  implicit final class CustomMapOps[K, V](val m: Map[K, V]) extends AnyVal {

    /**
      * This operation wraps map with Option, depending on it's emptiness.
      *
      * Mainly used in db layer, when we convert empty maps to NULL because reasons.
    **/
    def maybeEmpty: Option[Map[K, V]] = if (m.nonEmpty) Some(m) else None
  }

}
