package io.hydrosphere.serving.manager.infrastructure.protocol

import io.hydrosphere.serving.manager.domain.application._
import io.hydrosphere.serving.manager.domain.host_selector.HostSelector
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionStatus}
import io.hydrosphere.serving.manager.domain.servable.Servable
import spray.json._


trait ModelJsonProtocol extends CommonJsonProtocol with ContractJsonProtocol {
  
  implicit val servableStatus = new RootJsonFormat[Servable.Status] {
    
    implicit val running = jsonFormat2(Servable.Status.Running.apply)
  
    object Keys {
      val Running = "running"
      val Starting = "starting"
      val Stopped = "stopped"
    }
    
    override def read(json: JsValue): Servable.Status = {
      val obj = json.asJsObject
      obj.fields.get("type") match {
        case Some(JsString(x)) => x match {
          case Keys.Running => running.read(obj)
          case Keys.Starting => Servable.Status.Starting
          case Keys.Stopped => Servable.Status.Stopped
          case x => throw new DeserializationException(s"Invalid type field: $x")
        }
        case x => throw new DeserializationException(s"Invalid type field: $x")
      }
    }
    
    override def write(obj: Servable.Status): JsValue = {
      obj  match {
        case Servable.Status.Starting => JsObject("type" -> JsString(Keys.Starting))
        case r: Servable.Status.Running =>
          val body = running.write(r).asJsObject
          val fields = body.fields + ("type" -> JsString(Keys.Running))
          JsObject(fields)
        case Servable.Status.Stopped => JsObject("type" -> JsString(Keys.Stopped))
      }
    }
  }

  implicit val dockerImageFormat = jsonFormat3(DockerImage.apply)

  implicit val modelFormat = jsonFormat2(Model)
  implicit val environmentFormat = jsonFormat3(HostSelector)
  implicit val versionStatusFormat = enumFormat(ModelVersionStatus)
  implicit val modelVersionFormat = jsonFormat13(ModelVersion.apply)
  implicit val serviceFormat = jsonFormat3(Servable.apply)

  implicit val detailedServiceFormat = jsonFormat2(ModelVariant.apply)
  implicit val applicationStageFormat = jsonFormat2(PipelineStage.apply)
  implicit val applicationExecutionGraphFormat = jsonFormat1(ApplicationExecutionGraph)
  implicit val applicationKafkaStreamingFormat = jsonFormat4(ApplicationKafkaStream)
  implicit val appStatusFormat = enumFormat(ApplicationStatus)
  implicit val applicationFormat = jsonFormat7(Application.apply)
}

object ModelJsonProtocol extends ModelJsonProtocol