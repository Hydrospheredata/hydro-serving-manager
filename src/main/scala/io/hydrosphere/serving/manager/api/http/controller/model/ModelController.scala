package io.hydrosphere.serving.manager.api.http.controller.model

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import cats.data.OptionT
import cats.effect.{ConcurrentEffect, ContextShift, Effect}
import cats.syntax.functor._
import io.hydrosphere.serving.manager.api.http.controller.AkkaHttpControllerDsl
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.DomainError.InvalidRequest
import io.hydrosphere.serving.manager.domain.model.{Model, ModelRepository, ModelService}
import io.hydrosphere.serving.manager.domain.model_build.BuildLoggingService
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionService, ModelVersionView}
import io.swagger.annotations._
import javax.ws.rs.Path
import streamz.converter._

import scala.concurrent.duration._
import scala.util.Try


@Path("/api/v2/model")
@Api(produces = "application/json", tags = Array("Model and Model Versions"))
class ModelController[F[_]](
  modelManagementService: ModelService[F],
  modelRepo: ModelRepository[F],
  modelVersionManagementService: ModelVersionService[F],
  buildLoggingService: BuildLoggingService[F],
)(
  implicit F: ConcurrentEffect[F],
  cs: ContextShift[F],
  system: ActorSystem,
  materializer: ActorMaterializer,
) extends AkkaHttpControllerDsl {
  implicit val ec = system.dispatcher

  @Path("/")
  @ApiOperation(value = "listModels", notes = "listModels", nickname = "listModels", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Model", response = classOf[Model], responseContainer = "List"),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def listModels = path("model") {
    get {
      completeF(modelRepo.all())
    }
  }

  @Path("/{modelId}")
  @ApiOperation(value = "getModel", notes = "getModel", nickname = "getModel", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "modelId", required = true, dataType = "long", paramType = "path", value = "modelId")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Model", response = classOf[Model]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def getModel = pathPrefix("model" / LongNumber) { id =>
    get {
      completeF {
        modelManagementService.get(id)
      }
    }
  }

  @Path("/upload")
  @ApiOperation(value = "Upload and release a model", notes = "Send POST multipart with 'payload'-tar.gz and 'metadata'-json parts", nickname = "uploadModel", httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "ModelUploadMetadata", required = true,
      dataTypeClass = classOf[ModelUploadMetadata], paramType = "body")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Model", response = classOf[ModelVersion]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def uploadModel = pathPrefix("model" / "upload") {
    post {
      getFileWithMeta[F, ModelUploadMetadata, ModelVersion] {
        case (Some(file), Some(meta)) =>
          logger.info(s"Upload request path=$file, metadata=$meta")
          modelManagementService.uploadModel(file, meta).map(x => x.started)
        case (None, _) => Effect[F].raiseError(InvalidRequest("Couldn't find a payload in request"))
        case (_, None) => Effect[F].raiseError(InvalidRequest("Couldn't find a metadata in request"))
      }
    }
  }

  @Path("/register")
  @ApiOperation(value = "Register an external model", notes = "Register an external model", nickname = "registerModel", httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "RegisterModelRequest", required = true,
      dataTypeClass = classOf[RegisterModelRequest], paramType = "body")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "ModelVersion", response = classOf[ModelVersion]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def registerModel = pathPrefix("model" / "register") {
    post {
      entity(as[RegisterModelRequest]) { req =>
        ???
      }
    }
  }

  @Path("/version")
  @ApiOperation(value = "All ModelVersion", notes = "All ModelVersion", nickname = "allModelVersions", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "ModelVersion", response = classOf[ModelVersionView], responseContainer = "List"),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def allModelVersions = path("model" / "version") {
    get {
      completeF(
        modelVersionManagementService.list
      )
    }
  }

  @Path("/version/{versionName}/{version}")
  @ApiOperation(value = "Get ModelVersion", notes = "Get ModelVersion", nickname = "getModelVersion", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "versionName", required = true, dataType = "string", paramType = "path", value = "modelId"),
    new ApiImplicitParam(name = "version", required = true, dataType = "long", paramType = "path", value = "modelId")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "ModelVersion", response = classOf[ModelVersion]),
    new ApiResponse(code = 404, message = "Not found"),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def getModelVersions = path("model" / "version" / Segment / LongNumber) { (name, version) =>
    get {
      completeF(
        modelVersionManagementService.get(name, version)
      )
    }
  }

  @Path("/{modelId}")
  @ApiOperation(value = "Delete model if not in app", notes = "Fails if any version of the model is deployed", nickname = "deleteModel", httpMethod = "DELETE")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "modelId", required = true, dataType = "long", paramType = "path", value = "modelId")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Model", response = classOf[Model]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def deleteModel = pathPrefix("model" / LongNumber) { modelId =>
    delete {
      completeF {
        modelManagementService.deleteModel(modelId)
      }
    }
  }

  def buildLogs = pathPrefix("model" / "version" / LongNumber / "logs") { versionId =>
    get {
      optionalHeaderValueByName("Last-Event-ID") { maybeId =>
        val streamIdx = maybeId
          .flatMap(v => Try(v.toInt).toOption)
          .map(i => i + 1) // browser has event with id `i` so we continue stream starting with the next line
          .getOrElse(0)
        completeF {
          OptionT(buildLoggingService.getLogs(versionId, streamIdx))
            .getOrElseF(F.raiseError(DomainError.notFound(s"Can't find logs for model version id = ${versionId}")))
            .map { stream =>
              val s = stream.zipWithIndex
                .map { case (log, id) => (log, id + streamIdx) }
                .map { case (log, id) => ServerSentEvent(log, id = Some(id.toString), eventType = Some("Log")) }
                .onComplete(fs2.Stream.emit[F, ServerSentEvent](ServerSentEvent("", `type` = "EndOfStream")))
              Source.fromGraph(s.toSource)
                .keepAlive(15.seconds, () => ServerSentEvent.heartbeat)
            }
        }
      }
    }
  }

  val routes: Route = listModels ~ getModel ~ uploadModel ~ allModelVersions ~ deleteModel ~ getModelVersions ~ buildLogs ~ registerModel
}