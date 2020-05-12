package io.hydrosphere.serving.manager.infrastructure.grpc

import cats.data.NonEmptyList
import cats.implicits._
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.domain.application.Application
import io.hydrosphere.serving.manager.domain.contract.{Contract, Signature}
import io.hydrosphere.serving.manager.domain.image.DockerImage
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionStatus}
import io.hydrosphere.serving.manager.domain.monitoring.{
  CustomModelMetricSpec,
  CustomModelMetricSpecConfiguration,
  ThresholdCmpOperator
}
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.hydrosphere.serving.manager.util.BiMap
import io.hydrosphere.serving.manager.{domain, grpc}

object Converters {

  final val statusMap: BiMap[Servable.Status, grpc.entities.Servable.ServableStatus.Recognized] =
    BiMap(
      Servable.Status.Serving      -> grpc.entities.Servable.ServableStatus.SERVING,
      Servable.Status.NotServing   -> grpc.entities.Servable.ServableStatus.NOT_SERVING,
      Servable.Status.NotAvailable -> grpc.entities.Servable.ServableStatus.NOT_AVAILABlE,
      Servable.Status.Starting     -> grpc.entities.Servable.ServableStatus.STARTING
    )

  def mapThresholdOperator(
      thresholdCmpOperator: ThresholdCmpOperator
  ): grpc.entities.ThresholdConfig.CmpOp =
    thresholdCmpOperator match {
      case ThresholdCmpOperator.Eq        => grpc.entities.ThresholdConfig.CmpOp.EQ
      case ThresholdCmpOperator.NotEq     => grpc.entities.ThresholdConfig.CmpOp.NOT_EQ
      case ThresholdCmpOperator.Greater   => grpc.entities.ThresholdConfig.CmpOp.GREATER
      case ThresholdCmpOperator.Less      => grpc.entities.ThresholdConfig.CmpOp.LESS
      case ThresholdCmpOperator.GreaterEq => grpc.entities.ThresholdConfig.CmpOp.GREATER_EQ
      case ThresholdCmpOperator.LessEq    => grpc.entities.ThresholdConfig.CmpOp.LESS_EQ
    }

  def fromMetricSpecConfig(
      specConfig: CustomModelMetricSpecConfiguration
  ): grpc.entities.CustomModelMetric = {
    val threshold =
      grpc.entities.ThresholdConfig(
        specConfig.threshold,
        mapThresholdOperator(specConfig.thresholdCmpOperator)
      )

    grpc.entities.CustomModelMetric(
      monitorModelId = specConfig.modelVersionId,
      threshold = Some(threshold),
      servable = specConfig.servable.map(fromServable)
    )
  }

  def fromMetricSpec(metricSpec: CustomModelMetricSpec): grpc.entities.MetricSpec =
    grpc.entities.MetricSpec(
      id = metricSpec.id,
      name = metricSpec.name,
      modelVersionId = metricSpec.modelVersionId,
      customModelConfig = fromMetricSpecConfig(metricSpec.config).some
    )

  def fromModelVersion(mv: domain.model_version.ModelVersion): grpc.entities.ModelVersion =
    mv match {
      case imv: ModelVersion.Internal =>
        val (image, sha) = toGDocker(imv.image)
        val (runtime, _) = toGDocker(imv.runtime)
        grpc.entities.ModelVersion(
          id = imv.id,
          version = imv.modelVersion,
          status = imv.status.toString,
          selector = imv.hostSelector.map(s => grpc.entities.HostSelector(s.id, s.name)),
          model = grpc.entities.Model(imv.model.id, imv.model.name).some,
          contract = Contract.toProto(imv.modelContract).some,
          image = image.some,
          imageSha = sha.getOrElse(""),
          runtime = runtime.some,
          metadata = imv.metadata
        )
      case emv: ModelVersion.External =>
        grpc.entities.ModelVersion(
          id = emv.id,
          version = emv.modelVersion,
          status = ModelVersionStatus.Released.toString,
          model = grpc.entities.Model(emv.model.id, emv.model.name).some,
          contract = Contract.toProto(emv.modelContract).some,
          metadata = emv.metadata
        )
    }

  def fromServable(s: domain.servable.Servable): grpc.entities.Servable = {
    val status =
      statusMap.forward.getOrElse(s.status, grpc.entities.Servable.ServableStatus.NOT_AVAILABlE)
    grpc.entities.Servable(
      host = s.host.getOrElse(""),
      port = s.port.getOrElse(0),
      modelVersion = fromModelVersion(s.modelVersion).some,
      name = s.fullName,
      status = status,
      metadata = s.metadata
    )
  }

  def fromApp(app: Application): grpc.entities.ServingApp = {
    val stages = toGStages(app)
    val contract =
      ModelContract(
        modelName = app.name,
        predict = Signature.toProto(app.executionGraph.signature).some
      )
    grpc.entities.ServingApp(
      id = app.id.toString,
      name = app.name,
      contract = contract.some,
      pipeline = stages.toList,
      metadata = app.metadata
    )
  }

  def toGServable(mv: Servable, weight: Int): grpc.entities.Servable =
    grpc.entities.Servable(
      host = mv.host.getOrElse(""),
      port = mv.port.getOrElse(0),
      weight = weight,
      modelVersion = fromModelVersion(mv.modelVersion).some,
      name = mv.fullName,
      metadata = mv.metadata
    )

  def toGStages(app: Application): NonEmptyList[grpc.entities.Stage] =
    app.executionGraph.nodes.zipWithIndex.map {
      case (st, i) =>
        val mapped =
          st.variants
            .map(x => x.servable.map(servable => toGServable(servable, x.weight)))
            .collect {
              case Some(converted) => converted
            } // NB(bulat) weird situation when some servables are absent
        grpc.entities.Stage(i.toString, Signature.toProto(st.signature).some, mapped)
    }

  def toGDocker(image: DockerImage): (grpc.entities.DockerImage, Option[String]) =
    grpc.entities.DockerImage(image.name, image.tag) -> None
}
