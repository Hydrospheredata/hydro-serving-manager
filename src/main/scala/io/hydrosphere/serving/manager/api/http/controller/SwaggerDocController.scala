package io.hydrosphere.serving.manager.api.http.controller

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.github.swagger.akka.SwaggerHttpService
import com.github.swagger.akka.model.{Info, License}
import io.swagger.v3.oas.models.ExternalDocumentation

class SwaggerDocController(
  val apiClasses: Set[Class[_]],
  val version: String
)(
  implicit val actorSystem: ActorSystem,
  implicit val materializer: Materializer
) extends SwaggerHttpService  {
  override val apiDocsPath = "docs"
  override def basePath: String = "/api/v2"
  override val info = Info(
    version = version,
    title = "Hydroserving Manager service",
    license = Some(License("Apache License 2.0", "https://github.com/Hydrospheredata/hydro-serving/blob/master/LICENSE"))
  )
  override val externalDocs = Some(
    new ExternalDocumentation()
    .description("Find out more about Hydroserving")
    .url("https://hydrosphere.io/serving-docs")
  )

  override def schemes: List[String] = "https" :: "http" :: Nil
}