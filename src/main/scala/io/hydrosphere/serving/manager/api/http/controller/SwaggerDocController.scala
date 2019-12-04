package io.hydrosphere.serving.manager.api.http.controller

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.github.swagger.akka.SwaggerHttpService
import com.github.swagger.akka.model.{Info, License}
import io.swagger.models.{ExternalDocs, Scheme}

class SwaggerDocController(
  val apiClasses: Set[Class[_]],
  val version: String
)(
  implicit val actorSystem: ActorSystem,
  implicit val materializer: ActorMaterializer
) extends SwaggerHttpService  {
  override val apiDocsPath = "docs"
//  override def basePath: String = "/api/v2"  // TODO need to shorten controller URLs, so swagger-ui can shorten links
  override val info = Info(
    version = version,
    title = "Hydroserving Manager service",
    license = Some(License("Apache License 2.0", "https://github.com/Hydrospheredata/hydro-serving/blob/master/LICENSE"))
  )
  override val externalDocs = Some(new ExternalDocs("Find out more about Hydroserving", "https://hydrosphere.io/serving-docs"))
  override def schemes: List[Scheme] = Scheme.HTTPS :: Scheme.HTTP :: Nil
}