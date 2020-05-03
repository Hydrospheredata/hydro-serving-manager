package io.hydrosphere.serving.manager.domain

import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}

@ConfiguredJsonCodec
sealed trait DomainError extends Throwable {
  def message: String

  override def getMessage: String = message
}

object DomainError {
  implicit val config = Configuration.default.withDiscriminator("error")

  final case class NotFound(message: String) extends DomainError

  final case class InvalidRequest(message: String) extends DomainError

  final case class InternalError(message: String) extends DomainError

  def notFound(message: String): DomainError = NotFound(message)

  def invalidRequest(message: String): DomainError = InvalidRequest(message)

  def internalError(message: String): DomainError = InternalError(message)
}
