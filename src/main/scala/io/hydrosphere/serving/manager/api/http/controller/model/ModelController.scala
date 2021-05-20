package io.hydrosphere.serving.manager.api.http.controller.model

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import cats.data.OptionT
import cats.effect.Async
import cats.effect.std.Dispatcher
import cats.syntax.functor._
import io.hydrosphere.serving.manager.api.http.controller.AkkaHttpControllerDsl
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.DomainError.InvalidRequest
import io.hydrosphere.serving.manager.domain.model.{ModelRepository, ModelService}
import io.hydrosphere.serving.manager.domain.model_build.BuildLoggingService
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionService}

import javax.ws.rs.Path
import scala.concurrent.duration._
import scala.util.Try
import streamz.converter._

@Path("/model")
class ModelController[F[_]](
    modelManagementService: ModelService[F],
    modelRepo: ModelRepository[F],
    modelVersionManagementService: ModelVersionService[F],
    buildLoggingService: BuildLoggingService[F]
)(implicit
    F: Async[F],
    system: ActorSystem,
    materializer: Materializer
) extends AkkaHttpControllerDsl {
  implicit val ec = system.dispatcher

  @Path("/")
  def listModels =
    path("model") {
      get {
        completeF {
          modelRepo.all()

        }
      }
    }

  @Path("/{modelId}")
  def getModel =
    pathPrefix("model" / LongNumber) { id =>
      get {
        completeF {
          modelManagementService.get(id)
        }
      }
    }

  @Path("/upload")
  def uploadModel =
    pathPrefix("model" / "upload") {
      post {
        getFileWithMeta[F, ModelUploadMetadata, ModelVersion.Internal] {
          case (Some(file), Some(meta)) =>
            logger.info(s"Upload request path=$file, metadata=$meta")
            modelManagementService.uploadModel(file, meta).map(x => x.started)
          case (None, _) =>
            F.raiseError(InvalidRequest("Couldn't find a payload in request"))
          case (_, None) =>
            F.raiseError(InvalidRequest("Couldn't find a metadata in request"))
        }
      }
    }

  @Path("/version")
  def allModelVersions =
    path("model" / "version") {
      get {
        completeF(
          modelVersionManagementService.list
        )
      }
    }

  @Path("/version/{versionName}/{version}")
  def getModelVersions =
    path("model" / "version" / Segment / LongNumber) { (name, version) =>
      get {
        completeF(
          modelVersionManagementService.get(name, version)
        )
      }
    }

  @Path("/{modelId}")
  def deleteModel =
    pathPrefix("model" / LongNumber) { modelId =>
      delete {
        completeF {
          modelManagementService.deleteModel(modelId)
        }
      }
    }

  def buildLogs =
    pathPrefix("model" / "version" / LongNumber / "logs") { versionId =>
      get {
        optionalHeaderValueByName("Last-Event-ID") { maybeId =>
          val streamIdx = maybeId
            .flatMap(v => Try(v.toInt).toOption)
            .map(i =>
              i + 1
            ) // browser has event with id `i` so we continue stream starting with the next line
            .getOrElse(0)
          completeF {
            OptionT(buildLoggingService.getLogs(versionId, streamIdx))
              .getOrElseF(
                F.raiseError(
                  DomainError.notFound(s"Can't find logs for model version id = ${versionId}")
                )
              )
              .map { stream =>
                val s = stream.zipWithIndex
                  .map { case (log, id) => (log, id + streamIdx) }
                  .map {
                    case (log, id) =>
                      ServerSentEvent(log, id = Some(id.toString), eventType = Some("Log"))
                  }
                  .onComplete(
                    fs2.Stream.emit[F, ServerSentEvent](ServerSentEvent("", `type` = "EndOfStream"))
                  )
                Source
                  .fromGraph(s.toSource)
                  .keepAlive(15.seconds, () => ServerSentEvent.heartbeat)
              }
          }
        }
      }
    }

  val routes: Route =
    listModels ~ getModel ~ uploadModel ~ allModelVersions ~ deleteModel ~ getModelVersions ~ buildLogs
}

object ModelController {
  def make[F[_]](
      modelManagementService: ModelService[F],
      modelRepo: ModelRepository[F],
      modelVersionManagementService: ModelVersionService[F],
      buildLoggingService: BuildLoggingService[F]
  )(implicit F: Async[F], system: ActorSystem, materializer: Materializer) =
    Dispatcher[F].map { implicit disp =>
      new ModelController[F](
        modelManagementService,
        modelRepo,
        modelVersionManagementService,
        buildLoggingService
      )
    }
}
