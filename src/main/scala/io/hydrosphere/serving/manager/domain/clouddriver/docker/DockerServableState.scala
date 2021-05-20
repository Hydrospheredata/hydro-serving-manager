package io.hydrosphere.serving.manager.domain.clouddriver.docker

import cats.Show
import cats.implicits._
import io.hydrosphere.serving.manager.domain.clouddriver.{
  ServableEvent,
  ServableNotReady,
  ServableReady,
  ServableStarting,
  ServableState
}

sealed trait ContainerStatus
final case object ContainerStarted             extends ContainerStatus
final case object ContainerReady               extends ContainerStatus
final case class ContainerStopped(msg: String) extends ContainerStatus

case class DockerServableState(status: ContainerStatus) extends ServableState

case object DockerServableState {
  implicit val rsFailedShow: Show[ContainerStopped] = Show.show(s => "Docker container:" + s.msg)
  def default: DockerServableState =
    new DockerServableState(ContainerStopped("unknown status"))

  def toServableEvent(state: ContainerStatus): ServableEvent =
    state match {
      case ContainerStarted    => ServableStarting
      case ContainerReady      => ServableReady(None)
      case s: ContainerStopped => ServableNotReady(s.show)
    }
}
