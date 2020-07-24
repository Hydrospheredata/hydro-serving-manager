package io.hydrosphere.serving.manager.util.grpc

import cats.data.NonEmptyList
import cats.implicits._
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.domain.application.Application.ReadyApp
import io.hydrosphere.serving.manager.domain.application.graph.Variant
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionStatus}
import io.hydrosphere.serving.manager.domain.monitoring.{CustomModelMetricSpec, CustomModelMetricSpecConfiguration, ThresholdCmpOperator}
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.hydrosphere.serving.manager.domain.servable.Servable.OkServable
import io.hydrosphere.serving.manager.grpc.entities.Servable.ServableStatus
import io.hydrosphere.serving.manager.grpc.entities.{CustomModelMetric, ServingApp, ThresholdConfig, Servable => GServable, Stage => GStage}
import io.hydrosphere.serving.manager.{domain, grpc}

object Converters {
  def mapThresholdOperator(thresholdCmpOperator: ThresholdCmpOperator): ThresholdConfig.CmpOp = {
    thresholdCmpOperator match {
      case ThresholdCmpOperator.Eq => ThresholdConfig.CmpOp.EQ
      case ThresholdCmpOperator.NotEq => ThresholdConfig.CmpOp.NOT_EQ
      case ThresholdCmpOperator.Greater => ThresholdConfig.CmpOp.GREATER
      case ThresholdCmpOperator.Less =>ThresholdConfig.CmpOp.LESS
      case ThresholdCmpOperator.GreaterEq =>ThresholdConfig.CmpOp.GREATER_EQ
      case ThresholdCmpOperator.LessEq =>ThresholdConfig.CmpOp.LESS_EQ
    }
  }

  def fromMetricSpecConfig(specConfig: CustomModelMetricSpecConfiguration): CustomModelMetric = {
    val threshold = ThresholdConfig(specConfig.threshold, mapThresholdOperator(specConfig.thresholdCmpOperator))

    CustomModelMetric(
      monitorModelId = specConfig.modelVersionId,
      threshold = Some(threshold),
      servable = specConfig.servable.map(fromServable)
    )
  }

  def fromMetricSpec(metricSpec: CustomModelMetricSpec): grpc.entities.MetricSpec = {
    grpc.entities.MetricSpec(
      id = metricSpec.id,
      name = metricSpec.name,
      modelVersionId = metricSpec.modelVersionId,
      customModelConfig = fromMetricSpecConfig(metricSpec.config).some,
    )
  }

  def fromModelVersion(mv: domain.model_version.ModelVersion): grpc.entities.ModelVersion = {
    mv match {
      case imv: ModelVersion.Internal =>
        grpc.entities.ModelVersion(
          id = imv.id,
          version = imv.modelVersion,
          status = imv.status.toString,
          selector = imv.hostSelector.map(s => grpc.entities.HostSelector(s.id, s.name)),
          model = imv.model.toGrpc.some,
          contract = imv.modelContract.some,
          image = grpc.entities.DockerImage(imv.image.name, imv.image.tag).some,
          imageSha = imv.image.sha256.getOrElse(""),
          runtime = grpc.entities.DockerImage(imv.runtime.name, imv.runtime.tag).some,
          metadata = imv.metadata,
          monitoringConfiguration = imv.monitoringConfiguration.toGrpc.some
        )
      case emv: ModelVersion.External =>
        grpc.entities.ModelVersion(
          id = emv.id,
          version = emv.modelVersion,
          status = ModelVersionStatus.Released.toString,
          model = emv.model.toGrpc.some,
          contract = emv.modelContract.some,
          metadata = emv.metadata,
          monitoringConfiguration = emv.monitoringConfiguration.toGrpc.some
        )
    }
  }

  def fromServable(s: domain.servable.Servable.GenericServable): grpc.entities.Servable = {
    val (status, host, port) = s.status match {
      case Servable.Serving(_, h, p) => (ServableStatus.SERVING, h, p)
      case Servable.NotServing(_, h, p) => (ServableStatus.NOT_SERVING, h.getOrElse(""), p.getOrElse(0))
      case Servable.NotAvailable(_, h, p) => (ServableStatus.NOT_AVAILABlE, h.getOrElse(""), p.getOrElse(0))
      case Servable.Starting(_, h, p) => (ServableStatus.STARTING, h.getOrElse(""), p.getOrElse(0))
    }
    grpc.entities.Servable(
      host = host,
      port = port,
      modelVersion = fromModelVersion(s.modelVersion).some,
      name = s.fullName,
      status = status,
      metadata = s.metadata
    )
  }

  def fromApp(app: ReadyApp): ServingApp = {
    val stages = toGStages(app)
    val contract = ModelContract(modelName = app.name, predict = app.signature.some)
    ServingApp(
      id = app.id.toString,
      name = app.name,
      contract = contract.some,
      pipeline = stages.toList,
      metadata = app.metadata
    )
  }

  def toGServable(mv: Variant[OkServable]): GServable = {
    GServable(
      host = mv.item.status.host,
      port = mv.item.status.port,
      weight = mv.weight,
      modelVersion = fromModelVersion(mv.item.modelVersion).some,
      name = mv.item.fullName,
      metadata = mv.item.metadata
    )
  }

  def toGStages(app: ReadyApp): NonEmptyList[GStage] = {
    app.status.stages.zipWithIndex.map {
      case (st, i) =>
        val mapped = st.variants.map(toGServable)
        GStage(i.toString, st.signature.some, mapped.toList)
    }
  }
}