package io.hydrosphere.serving.manager.util

import cats.Show

/**
  * Wrapper for types that need to be hidden.
  * @param value value to hide
  * @tparam T
  */
case class Secret[T](value: T) extends AnyVal {
  override def toString: String = Secret.SECRET_PLACEHOLDER
}

object Secret {
  final val SECRET_PLACEHOLDER                = "[HIDDEN]"
  implicit def secretShow[T]: Show[Secret[T]] = Show.show(_ => SECRET_PLACEHOLDER)
}
