package io.hydrosphere.serving.manager.domain.clouddriver

sealed trait CloudInstanceEventAdapterError extends Throwable

case object MissingLabel extends CloudInstanceEventAdapterError

case object UnhandledEvent extends CloudInstanceEventAdapterError

final case class MissingField(message: String) extends CloudInstanceEventAdapterError

trait CloudInstanceEventAdapter[A] {
  def toEvent(value: A): Either[CloudInstanceEventAdapterError, CloudInstanceEvent]
}
