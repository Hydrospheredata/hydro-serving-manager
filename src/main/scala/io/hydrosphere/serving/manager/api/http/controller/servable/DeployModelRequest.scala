package io.hydrosphere.serving.manager.api.http.controller.servable

case class DeployModelRequest(modelName: String, version: Long, metadata: Option[Map[String, String]])
