package io.hydrosphere.serving.manager.domain.servable

import io.hydrosphere.serving.manager.domain.clouddriver.CloudInstance
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion

case class Servable[+T <: Servable.Status](
  modelVersion: ModelVersion,
  nameSuffix: String,
  status: T,
) {
  def fullName = Servable.fullName(modelVersion.model.name, modelVersion.modelVersion, nameSuffix)
  def generic: Servable[Servable.Status] = this
}

object Servable {

  type GenericServable = Servable[Status]

  sealed trait Status
  final case class Serving(msg: String, host: String, port: Int) extends Status
  final case class NotServing(msg: String, host: String, port: Int) extends Status
  final case class NotAvailable(msg: String) extends Status
  final case class Unknown(msg: String, host: Option[String], port: Option[Int]) extends Status

  def fullName(modelName: String, modelVersion: Long, suffix: String): String = s"$modelName-$modelVersion-$suffix".replace("_", "-")

  type OkServable = Servable[Serving]
  type NotOkServable = Servable[NotServing]
  type UnknownServable = Servable[Unknown]
}