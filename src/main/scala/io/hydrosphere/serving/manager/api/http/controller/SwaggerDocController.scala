package io.hydrosphere.serving.manager.api.http.controller

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.github.swagger.akka.SwaggerHttpService
import com.github.swagger.akka.model.{Info, License}
import io.hydrosphere.serving.manager.api.http.controller.application.ApplicationController
import io.hydrosphere.serving.manager.api.http.controller.host_selector.HostSelectorController
import io.hydrosphere.serving.manager.api.http.controller.model.{
  ExternalModelController,
  ModelController
}
import io.hydrosphere.serving.manager.api.http.controller.servable.ServableController
import io.swagger.v3.oas.models.ExternalDocumentation

class SwaggerDocController[F[_]](
    emCtr: ExternalModelController[F],
    mCtr: ModelController[F],
    appCtr: ApplicationController[F],
    hsCtr: HostSelectorController[F],
    sCtr: ServableController[F],
    monCtr: MonitoringController[F]
)(
    implicit val actorSystem: ActorSystem,
    implicit val materializer: Materializer
) extends SwaggerHttpService {

  override val apiDocsPath      = "docs"
  override val basePath: String = "/api/v2"
  override val info: Info = Info(
    version = "2",
    title = "Hydroserving Manager service",
    license = Some(
      License(
        "Apache License 2.0",
        "https://github.com/Hydrospheredata/hydro-serving/blob/master/LICENSE"
      )
    )
  )
  override val externalDocs: Option[ExternalDocumentation] = Some(
    new ExternalDocumentation()
      .description("Find out more about Hydroserving")
      .url("https://hydrosphere.io/serving-docs")
  )

  override val schemes: List[String] = "https" :: "http" :: Nil

  override val apiClasses: Set[Class[_]] = List(
    emCtr.getClass,
    mCtr.getClass,
    appCtr.getClass,
    hsCtr.getClass,
    sCtr.getClass,
    monCtr.getClass
  ).toSet
}
