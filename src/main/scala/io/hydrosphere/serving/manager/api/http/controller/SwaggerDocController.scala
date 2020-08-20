package io.hydrosphere.serving.manager.api.http.controller

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import spray.json.JsValue

class SwaggerDocController(
  val openApi: JsValue
)(
  implicit val actorSystem: ActorSystem,
  implicit val materializer: ActorMaterializer
) extends AkkaHttpControllerDsl {
  val routes: Route = pathPrefix("docs") {
    path("swagger.json") {
      get {
        complete(openApi)
      }
    }
  }
}

///**
// * DO NOT USE THIS.
// * CRASHES SWAGGER LIBRARY!!!!!!!!!
// * @param apiClasses
// * @param version
// * @param actorSystem
// * @param materializer
// */
//class SwaggerDocController(
//  val apiClasses: Set[Class[_]],
//  val version: String
//)(
//  implicit val actorSystem: ActorSystem,
//  implicit val materializer: ActorMaterializer
//) extends SwaggerHttpService  {
//  override val apiDocsPath = "docs"
//  override def basePath: String = "/api/v2"
//  override val info = Info(
//    version = version,
//    title = "Hydroserving Manager service",
//    license = Some(License("Apache License 2.0", "https://github.com/Hydrospheredata/hydro-serving/blob/master/LICENSE"))
//  )
//  override val externalDocs = Some(new ExternalDocs("Find out more about Hydroserving", "https://hydrosphere.io/serving-docs"))
//  override def schemes: List[Scheme] = Scheme.HTTPS :: Scheme.HTTP :: Nil
//}
