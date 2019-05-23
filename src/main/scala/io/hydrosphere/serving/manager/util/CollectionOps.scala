package io.hydrosphere.serving.manager.util

import cats.Order
import cats.data.{NonEmptyList, NonEmptySet}

object CollectionOps {

  implicit final class NonEmptyListOps[T](val l: NonEmptyList[T]) extends AnyVal {
    def toSet(implicit o: Order[T]): NonEmptySet[T] = {
      NonEmptySet.of(l.head, l.tail: _*)
    }
  }

}