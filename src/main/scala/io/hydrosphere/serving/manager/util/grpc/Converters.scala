package io.hydrosphere.serving.manager.util.grpc

import cats.data.NonEmptyList
import cats.implicits._
import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.manager.domain.application.Application.ReadyApp
import io.hydrosphere.serving.manager.domain.application.graph.Variant
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.hydrosphere.serving.manager.domain.servable.Servable.OkServable
import io.hydrosphere.serving.manager.{domain, grpc}
import io.hydrosphere.serving.manager.grpc.entities.{ServingApp, Servable => GServable, Stage => GStage}

object Converters {
   def fromModelVersion(mv: domain.model_version.ModelVersion): grpc.entities.ModelVersion = grpc.entities.ModelVersion(
      id = mv.id,
      version = mv.modelVersion,
      modelType = "",
      status = mv.status.toString,
      selector = mv.hostSelector.map(s => grpc.entities.HostSelector(s.id, s.name)),
      model = Some(grpc.entities.Model(mv.model.id, mv.model.name)),
      contract = Some(ModelContract(mv.modelContract.modelName, mv.modelContract.predict)),
      image = Some(grpc.entities.DockerImage(mv.image.name, mv.image.tag)),
      imageSha = mv.image.sha256.getOrElse(""),
      runtime = Some(grpc.entities.DockerImage(mv.runtime.name, mv.runtime.tag))
   )

   def fromServable(s: domain.servable.Servable.GenericServable): grpc.entities.Servable = {
      val (status, host, port) = s.status match {
         case Servable.Serving(_, h, p) => ("Serving", h, p)
         case Servable.NotServing(_, h, p) => ("NotServing", h.getOrElse(""), p.getOrElse(0))
         case Servable.NotAvailable(_, h, p) => ("NotAvailable", h.getOrElse(""), p.getOrElse(0))
         case Servable.Starting(_, h, p) => ("Starting", h.getOrElse(""), p.getOrElse(0))
      }
      grpc.entities.Servable(
         host = host,
         port = port,
         modelVersion = fromModelVersion(s.modelVersion).some,
         name = s.fullName
      )
   }

    def fromApp(app: ReadyApp): ServingApp = {
      val stages = toGStages(app)

      val contract = ModelContract(modelName = app.name, predict = app.signature.some)

      ServingApp(app.id.toString, app.name, contract.some, stages.toList)
    }

    def modelVersionToGrpcEntity(mv: domain.model_version.ModelVersion): grpc.entities.ModelVersion =
      grpc.entities.ModelVersion(
        id = mv.id,
        version = mv.modelVersion,
        modelType = "",
        status = mv.status.toString,
        selector = mv.hostSelector.map(s => grpc.entities.HostSelector(s.id, s.name)),
        model = grpc.entities.Model(mv.model.id, mv.model.name).some,
        contract = ModelContract(mv.modelContract.modelName, mv.modelContract.predict).some,
        image = grpc.entities.DockerImage(mv.image.name, mv.image.tag).some,
        imageSha = mv.image.sha256.getOrElse(""),
        runtime = grpc.entities.DockerImage(mv.runtime.name, mv.runtime.tag).some
      )

    def toGServable(mv: Variant[OkServable]): GServable = {
      GServable(
        host = mv.item.status.host,
        port = mv.item.status.port,
        weight = mv.weight,
        modelVersion = modelVersionToGrpcEntity(mv.item.modelVersion).some,
        name = mv.item.fullName
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
