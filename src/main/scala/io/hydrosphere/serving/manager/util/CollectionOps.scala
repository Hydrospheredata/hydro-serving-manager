package io.hydrosphere.serving.manager.util

import cats.Order
import cats.data.{NonEmptyList, NonEmptySet}

object CollectionOps {

  implicit final class NonEmptyListOps[T](val l: NonEmptyList[T]) extends AnyVal {
    def toSet(implicit o: Order[T]): NonEmptySet[T] = {
      NonEmptySet.of(l.head, l.tail: _*)
    }
  }

  implicit final class CustomMapOps[K, V](val m: Map[K, V]) extends AnyVal {
    /**
     * This operation wraps map with Option, depending on it's emptiness.
     * 
     * Mainly used in db layer, when we convert empty maps to NULL because reasons.
    **/
    def maybeEmpty: Option[Map[K, V]] = if (m.nonEmpty) Some(m) else None
  }

}