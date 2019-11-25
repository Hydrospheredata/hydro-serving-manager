package io.hydrosphere.serving.manager.domain.servable

import io.hydrosphere.serving.manager.domain.model_version.ModelVersion

case class Servable[+T <: Servable.Status](
  modelVersion: ModelVersion,
  nameSuffix: String,
  status: T,
  usedApps: List[String],
  metadata: Map[String, String] = Map.empty
) {
  def fullName: String = Servable.fullName(modelVersion.model.name, modelVersion.modelVersion, nameSuffix)
}

object Servable {

  sealed trait Status extends Product with Serializable
  final case class Serving(msg: String, host: String, port: Int)                      extends Status
  final case class NotServing(msg: String, host: Option[String], port: Option[Int])   extends Status
  final case class NotAvailable(msg: String, host: Option[String], port: Option[Int]) extends Status
  final case class Starting(msg: String, host: Option[String], port: Option[Int])     extends Status

  def fullName(modelName: String, modelVersion: Long, suffix: String): String =
    s"$modelName-$modelVersion-$suffix".replace("_", "-")

  def extractSuffix(modelName: String, modelVersion: Long, name: String): String = {
    name.replaceFirst(s"${modelName.replace("_", "-")}-$modelVersion-", "")
  }

  type GenericServable = Servable[Status]
  type OkServable      = Servable[Serving]
  type NotOkServable   = Servable[NotServing]
  type UnknownServable = Servable[Starting]
}