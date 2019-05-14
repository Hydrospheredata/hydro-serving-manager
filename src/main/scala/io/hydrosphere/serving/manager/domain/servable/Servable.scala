package io.hydrosphere.serving.manager.domain.servable


case class Servable(
  modelVersionId: Long,
  serviceName: String,
  status: Servable.Status
)

object Servable {
  sealed trait Status
  object Status {
    case object Starting extends Status
    final case class Running(host: String, port: Int) extends Status
    case object Stopped extends Status
  }
}
