package io.hydrosphere.serving.manager.api.http.controller

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.github.swagger.akka.SwaggerHttpService
import com.github.swagger.akka.model.Info
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
  override val info = Info(version = version, title = "Hydroserving Manager service")
  override val externalDocs = Some(new ExternalDocs("Github repository", "https://github.com/Hydrospheredata/hydro-serving"))
  override def schemes: List[Scheme] = Scheme.HTTPS :: Scheme.HTTP :: Nil
}