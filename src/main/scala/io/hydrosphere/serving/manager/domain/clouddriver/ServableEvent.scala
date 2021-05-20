package io.hydrosphere.serving.manager.domain.clouddriver

trait ServableEvent
case object ServableStarting                      extends ServableEvent
case class ServableReady(message: Option[String]) extends ServableEvent
case class ServableNotReady(message: String)      extends ServableEvent
