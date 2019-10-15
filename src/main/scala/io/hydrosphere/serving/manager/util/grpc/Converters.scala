package io.hydrosphere.serving.manager.util.grpc

import cats.implicits._
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.domain.application.Application
import io.hydrosphere.serving.manager.domain.application.graph.ExecutionGraph
import io.hydrosphere.serving.manager.domain.application.graph.compat.Variant
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.hydrosphere.serving.manager.grpc.entities.Servable.ServableStatus
import io.hydrosphere.serving.manager.grpc.entities.{ServingApp, Servable => GServable, Stage => GStage}
import io.hydrosphere.serving.manager.{domain, grpc}

object Converters {
  def fromModelVersion(mv: domain.model_version.ModelVersion): grpc.entities.ModelVersion = grpc.entities.ModelVersion(
    id = mv.id,
    version = mv.modelVersion,
    modelType = "",
    status = mv.status.toString,
    selector = mv.hostSelector.map(s => grpc.entities.HostSelector(s.id, s.name)),
    model = Some(grpc.entities.Model(mv.model.id, mv.model.name)),
    contract = Some(ModelContract(mv.contract.modelName, mv.contract.predict.some)),
    image = Some(grpc.entities.DockerImage(mv.image.name, mv.image.tag)),
    imageSha = mv.image.sha256.getOrElse(""),
    runtime = Some(grpc.entities.DockerImage(mv.runtime.name, mv.runtime.tag)),
    metadata = mv.metadata
  )

  def fromServable(s: domain.servable.Servable): grpc.entities.Servable = {
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

  def fromApp(app: Application): ServingApp = {
    val stages = toGStages(app)
    val contract = ModelContract(modelName = app.name, predict = app.graph.signature.some)
    ServingApp(
      id = app.id.toString,
      name = app.name,
      contract = contract.some,
      pipeline = stages,
      metadata = app.metadata
    )
  }

  def modelVersionToGrpcEntity(mv: domain.model_version.ModelVersion): grpc.entities.ModelVersion =
    grpc.entities.ModelVersion(
      id = mv.id,
      version = mv.modelVersion,
      modelType = "",
      status = mv.status.toString,
      selector = mv.hostSelector.map(s => grpc.entities.HostSelector(s.id, s.name)),
      model = grpc.entities.Model(mv.model.id, mv.model.name).some,
      contract = ModelContract(mv.contract.modelName, mv.contract.predict.some).some,
      image = grpc.entities.DockerImage(mv.image.name, mv.image.tag).some,
      imageSha = mv.image.sha256.getOrElse(""),
      runtime = grpc.entities.DockerImage(mv.runtime.name, mv.runtime.tag).some,
      metadata = mv.metadata
    )

  def toGServable(mv: Variant[Servable]): GServable = {
    val (host, port) = mv.item.status match {
      case Servable.Serving(msg, host, port) => host -> port
      case Servable.NotServing(msg, host, port) => host.getOrElse("") -> port.getOrElse(0)
      case Servable.NotAvailable(msg, host, port) => host.getOrElse("") -> port.getOrElse(0)
      case Servable.Starting(msg, host, port) => host.getOrElse("") -> port.getOrElse(0)
    }
    GServable(
      host = host,
      port = port,
      weight = mv.weight,
      modelVersion = modelVersionToGrpcEntity(mv.item.modelVersion).some,
      name = mv.item.fullName,
      metadata = mv.item.metadata
    )
  }

  def toGStages(app: Application): List[GStage] = {
    app.graph.nodes.collect {
      case ExecutionGraph.ModelNode(id, servable) =>
        val grpcServable = toGServable(Variant(servable, 100))
        GStage(id, servable.modelVersion.contract.predict.some, Seq(grpcServable))
      case ExecutionGraph.ABNode(id, submodels, contract) =>
        val grpcServables = submodels.map {
          case (s, weight) => toGServable(Variant(s.servable, weight))
        }
        GStage(id, contract.predict.some, grpcServables.toList)
    }
  }
}