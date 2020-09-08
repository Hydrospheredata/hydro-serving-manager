package io.hydrosphere.serving.manager.infrastructure.protocol

import io.hydrosphere.serving.manager.api.http.controller.application.ApplicationView
import io.hydrosphere.serving.manager.api.http.controller.model.{ModelUploadMetadata, RegisterModelRequest}
import io.hydrosphere.serving.manager.api.http.controller.servable.DeployModelRequest
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.model_version.ModelVersionView
import spray.json._

trait CompleteJsonProtocol extends CommonJsonProtocol with ContractJsonProtocol with ModelJsonProtocol {

  implicit val errorFormat = new RootJsonFormat[DomainError] {
    override def write(obj: DomainError): JsValue = {
      obj match {
        case x: DomainError.NotFound => JsObject(Map(
          "error" -> JsString("NotFound"),
          "message" -> JsString(x.message)
        ))
        case x: DomainError.InvalidRequest => JsObject(Map(
          "error" -> JsString("InvalidRequest"),
          "message" -> JsString(x.message)
        ))
        case x: DomainError.InternalError => JsObject(Map(
          "error" -> JsString("InternalError"),
          "information" -> JsString(x.message)
        ))
        case x => JsObject(Map(
          "error" -> JsString("DomainError"),
          "information" -> JsString(x.message)
        ))
      }
    }

    override def read(json: JsValue): DomainError = throw DeserializationException("Can't deserealize DomainError")
  }

  implicit val modelUpload = jsonFormat7(ModelUploadMetadata.apply)

  implicit val versionView = jsonFormat13(ModelVersionView.apply)
  implicit val deployModelFormat = jsonFormat4(DeployModelRequest.apply)

  implicit val modelRegister = jsonFormat3(RegisterModelRequest.apply)
}

object CompleteJsonProtocol extends CompleteJsonProtocol
