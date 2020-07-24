package io.hydrosphere.serving.manager.infrastructure.protocol

import io.hydrosphere.serving.manager.domain.application._
import io.hydrosphere.serving.manager.domain.application.graph._
import io.hydrosphere.serving.manager.domain.application.graph.VersionGraphComposer.PipelineStage
import io.hydrosphere.serving.manager.domain.clouddriver
import io.hydrosphere.serving.manager.domain.clouddriver.CloudInstance
import io.hydrosphere.serving.manager.domain.host_selector.HostSelector
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model.Model
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionStatus}
import io.hydrosphere.serving.manager.domain.monitoring.{CustomModelMetricSpec, CustomModelMetricSpecConfiguration, ThresholdCmpOperator}
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBMonitoringRepository.CustomModelConfigRow
import spray.json._

import scala.util.Try

trait ModelJsonProtocol extends CommonJsonProtocol with ContractJsonProtocol {

  implicit val cloudServableStatus = new RootJsonFormat[CloudInstance.Status] {

    implicit val running = jsonFormat2(CloudInstance.Status.Running.apply)

    object Keys {
      val Running  = "running"
      val Starting = "starting"
      val Stopped  = "stopped"
    }

    override def read(json: JsValue): clouddriver.CloudInstance.Status = {
      val obj = json.asJsObject
      obj.fields.get("type") match {
        case Some(JsString(x)) =>
          x match {
            case Keys.Running  => running.read(obj)
            case Keys.Starting => CloudInstance.Status.Starting
            case Keys.Stopped  => CloudInstance.Status.Stopped
            case x             => throw DeserializationException(s"Invalid type field: $x")
          }
        case x => throw DeserializationException(s"Invalid type field: $x")
      }
    }

    override def write(obj: clouddriver.CloudInstance.Status): JsValue = {
      obj match {
        case CloudInstance.Status.Starting => JsObject("type" -> JsString(Keys.Starting))
        case r: CloudInstance.Status.Running =>
          val body   = running.write(r).asJsObject
          val fields = body.fields + ("type" -> JsString(Keys.Running))
          JsObject(fields)
        case CloudInstance.Status.Stopped => JsObject("type" -> JsString(Keys.Stopped))
      }
    }
  }

  implicit val dockerImageFormat = jsonFormat3(DockerImage.apply)

  implicit val modelFormat         = jsonFormat2(Model)
  implicit val environmentFormat   = jsonFormat3(HostSelector)
  implicit val versionStatusFormat = enumFormat(ModelVersionStatus)
  implicit val internalModelVersionFormat  = jsonFormat13(ModelVersion.Internal.apply)
  implicit val externalModelVersionFormat  = jsonFormat7(ModelVersion.External.apply)
  implicit val modelVersionFormat = new RootJsonWriter[ModelVersion] {
    override def write(obj: ModelVersion): JsValue = {
      val fields = obj match {
        case x: ModelVersion.Internal => x.toJson.asJsObject.fields ++ Map("kind" -> JsString("Internal"))
        case x: ModelVersion.External => x.toJson.asJsObject.fields ++ Map("kind" -> JsString("External"))
      }
      JsObject(fields)
    }

    //    override def read(json: JsValue): ModelVersion = {
    //      json match {
    //        case JsObject(fields) =>
    //          fields.get("kind") match {
    //            case Some(value) =>
    //              value match {
    //                case JsString(value) =>
    //                  value match {
    //                    case "Internal" => JsObject(fields.filterKeys(x => x != "kind")).convertTo[ModelVersion.Internal]
    //                    case "External" => JsObject(fields.filterKeys(x => x != "kind")).convertTo[ModelVersion.External]
    //                  }
    //                case x => throw DeserializationException(s"Cannot deserialize ModelVersion with invalid 'kind' field: ${x}")
    //              }
    //            case None => throw DeserializationException("Cannot deserialize ModelVersion without 'kind' field")
    //          }
    //        case _ => throw DeserializationException("Invalid ModelVersion Json")
    //      }
    //    }
  }

  implicit val cloudServableFormat = jsonFormat3(CloudInstance.apply)

  implicit def variantFormat[T: JsonFormat] = jsonFormat2(Variant.apply[T])

  implicit val modelVariant   = jsonFormat2(ModelVariant.apply)
  implicit val versionStage   = jsonFormat2(VersionStage.apply)
  implicit val versionAdapter = jsonFormat1(VersionGraphAdapter.apply)

  implicit val servingSF  = jsonFormat3(Servable.Serving)
  implicit val servingNSF = jsonFormat3(Servable.NotServing)
  implicit val servingNAF = jsonFormat3(Servable.NotAvailable)
  implicit val servingUF  = jsonFormat3(Servable.Starting)

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
          x.toJson.asJsObject.fields ++ Map("status" -> JsString("Starting"))
      }
      JsObject(fields)
    }

    override def read(json: JsValue): Servable.Status = {
      json match {
        case JsObject(fields) =>
          fields.get("status") match {
            case Some(JsString("Serving"))      => json.convertTo[Servable.Serving]
            case Some(JsString("NotServing"))   => json.convertTo[Servable.NotServing]
            case Some(JsString("NotAvailable")) => json.convertTo[Servable.NotAvailable]
            case Some(JsString("Starting"))     => json.convertTo[Servable.Starting]
            case Some(JsString("Unknown"))      => json.convertTo[Servable.Starting]
            case x                              => throw DeserializationException(s"Invalid Servable status type: $x")
          }
        case x => throw DeserializationException(s"Invalid Servable status JSON: $x")
      }
    }
  }

  implicit def servableFormat[T <: Servable.Status](implicit j: JsonFormat[T]) =
    jsonFormat5(Servable.apply[T])

  implicit val servableStageFormat = jsonFormat2(ServableStage.apply)
  implicit val servableAdapter     = jsonFormat1(ServableGraphAdapter.apply)

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

  implicit val applicationStageFormat          = jsonFormat2(PipelineStage.apply)
  implicit val applicationKafkaStreamingFormat = jsonFormat4(ApplicationKafkaStream)
  implicit val execNode = jsonFormat2(ExecutionNode.apply)

  implicit val cmpOp = new RootJsonFormat[ThresholdCmpOperator] {
    override def write(obj: ThresholdCmpOperator): JsValue = {
      val kind = obj match {
        case ThresholdCmpOperator.Eq => JsString("Eq")
        case ThresholdCmpOperator.NotEq => JsString("NotEq")
        case ThresholdCmpOperator.Greater => JsString("Greater")
        case ThresholdCmpOperator.Less => JsString("Less")
        case ThresholdCmpOperator.GreaterEq => JsString("GreaterEq")
        case ThresholdCmpOperator.LessEq => JsString("LessEq")
      }
      JsObject(Map("kind" -> kind))
    }

    override def read(json: JsValue): ThresholdCmpOperator = {
      json match {
        case JsObject(fields) =>
          fields.get("kind") match {
            case Some(kind) =>
              kind match {
                case JsString(value) =>
                  value match {
                    case "Eq" => ThresholdCmpOperator.Eq
                    case "NotEq" => ThresholdCmpOperator.NotEq
                    case "Greater" => ThresholdCmpOperator.Greater
                    case "Less" => ThresholdCmpOperator.Less
                    case "GreaterEq" => ThresholdCmpOperator.GreaterEq
                    case "LessEq" => ThresholdCmpOperator.LessEq
                    case x => throw DeserializationException(s"Invalid ThresholdCmpOperator kind $x")
                  }
                case x => throw DeserializationException(s"Invalid ThresholdCmpOperator kind type. Expected string, got: $x")
              }
            case None => throw DeserializationException(s"Invalid ThresholdCmpOperator json. 'kind' field expected $fields")
          }
        case x => throw DeserializationException(s"Invalid ThresholdCmpOperator json $x")
      }
    }
  }
  implicit val customSpecConfig = jsonFormat4(CustomModelMetricSpecConfiguration.apply)
  implicit val customSpec = jsonFormat4(CustomModelMetricSpec.apply)
  implicit val customModelConfigRow = jsonFormat4(CustomModelConfigRow.apply)
}

object ModelJsonProtocol extends ModelJsonProtocol
