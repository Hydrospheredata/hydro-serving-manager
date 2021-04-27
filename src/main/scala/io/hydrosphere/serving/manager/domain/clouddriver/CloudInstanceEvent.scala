package io.hydrosphere.serving.manager.domain.clouddriver

sealed trait CloudInstanceEvent {
  def instanceName: String
}

final case class Starting(instanceName: String, warning: Option[String] = None) extends CloudInstanceEvent

final case class Ready(instanceName: String, warning: Option[String] = None) extends CloudInstanceEvent

final case class NotAvailable(instanceName: String, message: String) extends CloudInstanceEvent

final case class NotServing(instanceName: String, message: String) extends CloudInstanceEvent

final case class Available(instanceName: String) extends CloudInstanceEvent