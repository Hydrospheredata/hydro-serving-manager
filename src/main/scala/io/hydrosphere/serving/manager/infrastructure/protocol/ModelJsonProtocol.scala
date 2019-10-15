package io.hydrosphere.serving.manager.infrastructure.protocol

import io.hydrosphere.serving.manager.domain.application._
import io.hydrosphere.serving.manager.domain.application.graph._
import io.hydrosphere.serving.manager.domain.application.graph.compat._
import io.hydrosphere.serving.manager.domain.{Contract, clouddriver}
import io.hydrosphere.serving.manager.domain.clouddriver.CloudInstance
import io.hydrosphere.serving.manager.domain.host_selector.HostSelector
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionStatus}
import io.hydrosphere.serving.manager.domain.servable.Servable
import spray.json._

import scala.util.Try

trait ModelJsonProtocol extends CommonJsonProtocol with ContractJsonProtocol {

  implicit val cloudServableStatus = new RootJsonFormat[CloudInstance.Status] {

    implicit val running = jsonFormat2(CloudInstance.Status.Running.apply)

    object Keys {
      val Running = "running"
      val Starting = "starting"
      val Stopped = "stopped"
    }

    override def read(json: JsValue): clouddriver.CloudInstance.Status = {
      val obj = json.asJsObject
      obj.fields.get("type") match {
        case Some(JsString(x)) =>
          x match {
            case Keys.Running => running.read(obj)
            case Keys.Starting => CloudInstance.Status.Starting
            case Keys.Stopped => CloudInstance.Status.Stopped
            case x => throw DeserializationException(s"Invalid type field: $x")
          }
        case x => throw DeserializationException(s"Invalid type field: $x")
      }
    }

    override def write(obj: clouddriver.CloudInstance.Status): JsValue = {
      obj match {
        case CloudInstance.Status.Starting => JsObject("type" -> JsString(Keys.Starting))
        case r: CloudInstance.Status.Running =>
          val body = running.write(r).asJsObject
          val fields = body.fields + ("type" -> JsString(Keys.Running))
          JsObject(fields)
        case CloudInstance.Status.Stopped => JsObject("type" -> JsString(Keys.Stopped))
      }
    }
  }

  implicit val dockerImageFormat = jsonFormat3(DockerImage.apply)

  implicit val modelFormat = jsonFormat2(Model)
  implicit val environmentFormat = jsonFormat3(HostSelector)
  implicit val versionStatusFormat = enumFormat(ModelVersionStatus)
  implicit val contractFormat = jsonFormat2(Contract.apply)
  implicit val modelVersionFormat = jsonFormat12(ModelVersion.apply)
  implicit val cloudServableFormat = jsonFormat3(CloudInstance.apply)

  implicit def variantFormat[T: JsonFormat] = jsonFormat2(Variant.apply[T])

  implicit val modelVariant = jsonFormat2(ModelVariant.apply)
  implicit val versionStage = jsonFormat2(VersionStage.apply)
  implicit val versionAdapter = jsonFormat1(VersionGraphAdapter.apply)

  implicit val servingSF = jsonFormat3(Servable.Serving)
  implicit val servingNSF = jsonFormat3(Servable.NotServing)
  implicit val servingNAF = jsonFormat3(Servable.NotAvailable)
  implicit val servingUF = jsonFormat3(Servable.Starting)

  implicit val servableStatusFormat = new RootJsonFormat[Servable.Status] {
    override def write(obj: Servable.Status): JsValue = {
      val fields = obj match {
        case x: Servable.Serving =>
          x.toJson.asJsObject.fields ++ Map("status" -> JsString("Serving"))
        case x: Servable.NotServing =>
          x.toJson.asJsObject.fields ++ Map("status" -> JsString("NotServing"))
        case x: Servable.NotAvailable =>
          x.toJson.asJsObject.fields ++ Map("status" -> JsString("NotAvailable"))
        case x: Servable.Starting =>
          x.toJson.asJsObject.fields ++ Map("status" -> JsString("Unknown"))
      }
      JsObject(fields)
    }

    override def read(json: JsValue): Servable.Status = {
      json match {
        case JsObject(fields) =>
          fields.get("status") match {
            case Some(JsString("Serving")) => json.convertTo[Servable.Serving]
            case Some(JsString("NotServing")) => json.convertTo[Servable.NotServing]
            case Some(JsString("NotAvailable")) => json.convertTo[Servable.NotAvailable]
            case Some(JsString("Unknown")) => json.convertTo[Servable.Starting]
            case x => throw DeserializationException(s"Invalid Servable status type: $x")
          }
        case x => throw DeserializationException(s"Invalid Servable status JSON: $x")
      }
    }
  }

  implicit val servableFormat = jsonFormat5(Servable.apply)

  implicit val servableStageFormat = jsonFormat2(ServableStage.apply)
  implicit val servableAdapter = jsonFormat1(ServableGraphAdapter.apply)

  implicit val executionGraphAdapterFormat = new RootJsonFormat[ExecutionGraphAdapter] {
    override def read(json: JsValue): ExecutionGraphAdapter = {
      json match {
        case x: JsObject =>
          Try(x.convertTo[VersionGraphAdapter])
            .orElse(Try(x.convertTo[ServableGraphAdapter]))
            .get
        case x => throw DeserializationException("Invalid JSON for ExecutionGraph")
      }
    }

    override def write(obj: ExecutionGraphAdapter): JsValue = {
      obj match {
        case x: VersionGraphAdapter => x.toJson
        case x: ServableGraphAdapter => x.toJson
      }
    }
  }

//  implicit val applicationStageFormat = jsonFormat2(PipelineStage.apply)
  implicit val applicationKafkaStreamingFormat = jsonFormat4(Application.KafkaParams)
  implicit val graphLinkFormat = jsonFormat2(ExecutionGraph.DirectionalLink.apply)
  implicit val modelNodeFormat = jsonFormat2(ExecutionGraph.ModelNode.apply)
  implicit val abNodeFormat = jsonFormat3(ExecutionGraph.ABNode.apply)
  implicit val internalNodeFormat = jsonFormat2(ExecutionGraph.InternalNode.apply)
  implicit val executionNodeFormat = new RootJsonFormat[ExecutionGraph.ExecutionNode] {
    override def write(obj: ExecutionGraph.ExecutionNode): JsValue = {
      val fields = obj match {
        case x: ExecutionGraph.ModelNode => x.toJson.asJsObject.fields ++ Map("type" -> JsString("ModelNode"))
        case x: ExecutionGraph.ABNode => x.toJson.asJsObject.fields ++ Map("type" -> JsString("ABNode"))
        case x: ExecutionGraph.InternalNode => x.toJson.asJsObject.fields ++ Map("type" -> JsString("InternalNode"))
      }
      JsObject(fields)
    }

    override def read(json: JsValue): ExecutionGraph.ExecutionNode = {
      json match {
        case JsObject(fields) =>
          fields.get("type") match {
            case Some(value) =>
              value match {
                case JsString(value) =>
                  value match {
                    case "ModelNode" => json.convertTo[ExecutionGraph.ModelNode]
                    case "ABNode" => json.convertTo[ExecutionGraph.ABNode]
                    case "InternalNode" => json.convertTo[ExecutionGraph.InternalNode]
                    case x =>throw DeserializationException(s"Invalid node type: ${x}")
                  }
                case x =>  throw DeserializationException(s"Invalid ExecutionNode json: ${json}")
              }
            case None => throw DeserializationException(s"Invalid ExecutionNode json: ${json}")
          }
        case x => throw DeserializationException(s"Invalid ExecutionNode json: ${x}")
      }
    }
  }
}

object ModelJsonProtocol extends ModelJsonProtocol