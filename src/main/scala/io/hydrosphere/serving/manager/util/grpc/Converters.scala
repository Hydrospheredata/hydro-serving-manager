package io.hydrosphere.serving.manager.util.grpc

import cats.data.NonEmptyList
import cats.implicits._
import io.hydrosphere.serving.manager.domain.application.{
  Application,
  ApplicationGraph,
  ApplicationServable
}
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionStatus}
import io.hydrosphere.serving.manager.domain.monitoring.{
  CustomModelMetricSpec,
  CustomModelMetricSpecConfiguration,
  ThresholdCmpOperator
}
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.hydrosphere.serving.manager.util.BiMap
import io.hydrosphere.serving.proto.manager.{entities => grpcEntity}

object Converters {
  final val servableStatusMap
      : BiMap[Servable.Status, grpcEntity.Servable.ServableStatus.Recognized] =
    BiMap(
      Servable.Status.Serving      -> grpcEntity.Servable.ServableStatus.SERVING,
      Servable.Status.NotServing   -> grpcEntity.Servable.ServableStatus.NOT_SERVING,
      Servable.Status.NotAvailable -> grpcEntity.Servable.ServableStatus.NOT_AVAILABlE,
      Servable.Status.Starting     -> grpcEntity.Servable.ServableStatus.STARTING
    )

  final val modelVersionStatusMap
      : BiMap[ModelVersionStatus, grpcEntity.ModelVersionStatus.Recognized] =
    BiMap(
      ModelVersionStatus.Assembling -> grpcEntity.ModelVersionStatus.Building,
      ModelVersionStatus.Released   -> grpcEntity.ModelVersionStatus.Success,
      ModelVersionStatus.Failed     -> grpcEntity.ModelVersionStatus.Failed
    )

  def mapThresholdOperator(
      thresholdCmpOperator: ThresholdCmpOperator
  ): grpcEntity.ThresholdConfig.CmpOp =
    thresholdCmpOperator match {
      case ThresholdCmpOperator.Eq        => grpcEntity.ThresholdConfig.CmpOp.EQ
      case ThresholdCmpOperator.NotEq     => grpcEntity.ThresholdConfig.CmpOp.NOT_EQ
      case ThresholdCmpOperator.Greater   => grpcEntity.ThresholdConfig.CmpOp.GREATER
      case ThresholdCmpOperator.Less      => grpcEntity.ThresholdConfig.CmpOp.LESS
      case ThresholdCmpOperator.GreaterEq => grpcEntity.ThresholdConfig.CmpOp.GREATER_EQ
      case ThresholdCmpOperator.LessEq    => grpcEntity.ThresholdConfig.CmpOp.LESS_EQ
    }

  def fromMetricSpecConfig(
      specConfig: CustomModelMetricSpecConfiguration
  ): grpcEntity.CustomModelMetric = {
    val threshold =
      grpcEntity.ThresholdConfig(
        specConfig.threshold,
        mapThresholdOperator(specConfig.thresholdCmpOperator)
      )

    grpcEntity.CustomModelMetric(
      monitorModelId = specConfig.modelVersionId,
      threshold = Some(threshold),
      servable = specConfig.servable.map(fromServable)
    )
  }

  def fromMetricSpec(metricSpec: CustomModelMetricSpec): grpcEntity.MetricSpec =
    grpcEntity.MetricSpec(
      id = metricSpec.id,
      name = metricSpec.name,
      modelVersionId = metricSpec.modelVersionId,
      customModelConfig = fromMetricSpecConfig(metricSpec.config).some
    )

  def fromModelVersion(mv: ModelVersion): grpcEntity.ModelVersion =
    mv match {
      case imv: ModelVersion.Internal =>
        grpcEntity.ModelVersion(
          id = imv.id,
          version = imv.modelVersion,
          status = modelVersionStatusMap.forward(imv.status),
          imageSha = imv.image.sha256.getOrElse(""),
          signature = Signature.toProto(imv.modelSignature).some,
          image = grpcEntity.DockerImage(imv.image.name, imv.image.tag).some,
          runtime = grpcEntity.DockerImage(imv.runtime.name, imv.runtime.tag).some,
          metadata = imv.metadata,
          monitoringConfiguration = Some(
            grpcEntity.MonitoringConfiguration(batchSize = imv.monitoringConfiguration.batchSize)
          )
        )
      case emv: ModelVersion.External =>
        grpcEntity.ModelVersion(
          id = emv.id,
          version = emv.modelVersion,
          status = grpcEntity.ModelVersionStatus.Success,
          signature = Signature.toProto(emv.modelSignature).some,
          metadata = emv.metadata,
          monitoringConfiguration = grpcEntity
            .MonitoringConfiguration(batchSize = emv.monitoringConfiguration.batchSize)
            .some
        )
    }

  def fromServable(s: Servable): grpcEntity.Servable = {
    val status =
      servableStatusMap.forward
        .getOrElse(s.status, grpcEntity.Servable.ServableStatus.NOT_AVAILABlE)

    grpcEntity.Servable(
      host = s.host.getOrElse(""),
      port = s.port.getOrElse(0),
      modelVersion = fromModelVersion(s.modelVersion).some,
      name = s.name,
      status = status,
      metadata = s.metadata
    )
  }

  def fromApp(app: Application): grpcEntity.Application = {
    val stages    = toGStages(app.graph)
    val signature = Signature.toProto(app.signature)

    grpcEntity.Application(
      id = app.id.toString,
      name = app.name,
      signature = signature.some,
      pipeline = stages.toList,
      metadata = app.metadata
    )
  }

  def toGServable(mv: ApplicationServable): Option[grpcEntity.Servable] =
    mv.servable.map { s =>
      grpcEntity.Servable(
        host = s.host.getOrElse(""),
        port = s.port.getOrElse(0),
        weight = mv.weight,
        modelVersion = fromModelVersion(mv.modelVersion).some,
        name = s.name,
        metadata = s.metadata
      )
    }

  def toGStages(graph: ApplicationGraph): NonEmptyList[grpcEntity.Stage] =
    graph.stages.zipWithIndex.map {
      case (st, i) =>
        val mapped = st.variants.toList.flatMap(toGServable)
        grpcEntity.Stage(i.toString, Signature.toProto(st.signature).some, mapped)
    }
}
