package io.hydrosphere.serving.manager.domain.clouddriver.k8s

import cats.Show
import cats.implicits._
import io.hydrosphere.serving.manager.domain.clouddriver.{
  ServableEvent,
  ServableNotReady,
  ServableReady,
  ServableStarting,
  ServableState
}

sealed trait RsStatus
final case class RsIsOk(msg: Option[String] = None) extends RsStatus
final case class RsIsFailed(msg: String)            extends RsStatus
case class RsInitialized()                          extends RsStatus

sealed trait SvcStatus
final case object SvcIsAvailable               extends SvcStatus
final case class SvcIsUnavailable(msg: String) extends SvcStatus
case class SvcInitialized()                    extends SvcStatus

case class K8sServableState(rs: RsStatus, svc: SvcStatus) extends ServableState

case object K8sServableState {
  implicit val rsFailedShow: Show[RsIsFailed]        = Show.show(s => "Replicaset:" + s.msg)
  implicit val svcFailedShow: Show[SvcIsUnavailable] = Show.show(s => "Service:" + s.msg)
  implicit val rsOkShow: Show[RsIsOk]                = Show.show(s => s.msg.map("Replicaset:" + _).getOrElse(""))

  def default: K8sServableState =
    new K8sServableState(RsInitialized(), SvcInitialized())

  def toServableEvent(state: K8sServableState): ServableEvent =
    state match {
      case K8sServableState(_: RsInitialized, _: SvcInitialized) => ServableStarting
      case K8sServableState(rs: RsIsFailed, svc: SvcIsUnavailable) =>
        ServableNotReady(rs.show + svc.show)
      case K8sServableState(_, s: SvcIsUnavailable) => ServableNotReady(s.show)
      case K8sServableState(s: RsIsFailed, _)       => ServableNotReady(s.show)
      case K8sServableState(s: RsIsOk, _)           => ServableReady(s.show.some)
    }
}
