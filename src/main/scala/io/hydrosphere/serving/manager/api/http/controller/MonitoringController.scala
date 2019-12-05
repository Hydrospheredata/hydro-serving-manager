package io.hydrosphere.serving.manager.api.http.controller

import akka.http.scaladsl.server.Route
import cats.data.OptionT
import cats.effect.Effect
import cats.implicits._
import io.hydrosphere.serving.manager.api.http.controller.MonitoringRequests._
import io.hydrosphere.serving.manager.api.http.controller.servable.ServableView
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.monitoring.{CustomModelMetricSpec, CustomModelMetricSpecConfiguration, Monitoring, MonitoringRepository, ThresholdCmpOperator}
import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol._
import io.hydrosphere.serving.manager.util.UUIDGenerator
import io.swagger.annotations.{Api, ApiImplicitParam, ApiImplicitParams, ApiOperation, ApiResponse, ApiResponses}
import javax.ws.rs.Path
import org.apache.logging.log4j.scala.Logging

@Path("/metricspec")
@Api(produces = "application/json", tags = Array("Metric Specifications"))
class MonitoringController[F[_]](
  monitoringService: Monitoring[F],
  monRepo: MonitoringRepository[F]
)(implicit F: Effect[F], uuid: UUIDGenerator[F]) extends AkkaHttpControllerDsl with Logging {
  implicit val configReq = jsonFormat3(MetricSpecConfigCreationRequest)
  implicit val specReq = jsonFormat3(MetricSpecCreationRequest)
  implicit val configView = jsonFormat4(MetricSpecConfigView)
  implicit val specView = jsonFormat4(MetricSpecView)

  @Path("/")
  @ApiOperation(value = "createMetricSpec", notes = "createMetricSpec", nickname = "createMetricSpec", httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "MetricSpecCreationRequest", required = true,
      dataTypeClass = classOf[MetricSpecCreationRequest], paramType = "body")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "MetricSpec", response = classOf[MetricSpecView]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def createSpec: Route = path("metricspec") {
    post {
      entity(as[MetricSpecCreationRequest]) { incomingMS =>
        logger.info(s"Got MetricSpec create request: $incomingMS")
        val flow = for {
          id <- uuid.generate()
          config = CustomModelMetricSpecConfiguration(
            incomingMS.config.modelVersionId,
            incomingMS.config.threshold,
            incomingMS.config.thresholdCmpOperator,
            None
          )
          ms = CustomModelMetricSpec(
            incomingMS.name,
            incomingMS.modelVersionId,
            config,
            id = id.toString
          )
          res <- monitoringService.create(ms)
        } yield fromMetricSpec(res)
        completeF(flow)
      }
    }
  }

  @Path("/")
  @ApiOperation(value = "listSpecs", notes = "listSpecs", nickname = "listSpecs", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "MetricSpec", response = classOf[MetricSpecView], responseContainer = "List"),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def listSpecs: Route = path("metricspec") {
    get {
      completeF(monitoringService.all().map(_.map(fromMetricSpec)))
    }
  }

  @Path("/{specId}")
  @ApiOperation(value = "getSpec", notes = "getSpec", nickname = "getSpec", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "specId", required = true, dataType = "string", paramType = "path", value = "specId"),
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "MetricSpec", response = classOf[MetricSpecView]),
    new ApiResponse(code = 404, message = "Not found"),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def getSpec: Route = path("metricspec" / Segment) { id =>
    get {
      val flow = OptionT(monRepo.get(id))
        .map(fromMetricSpec)
        .getOrElseF(DomainError.notFound(s"Can't find metricspec id=$id").raiseError[F, MetricSpecView])
      completeF(flow)
    }
  }

  @Path("/modelversion/{versionId}")
  @ApiOperation(value = "getSpecForModelVersion", notes = "getSpecForModelVersion", nickname = "getSpecForModelVersion", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "versionId", required = true, dataType = "string", paramType = "path", value = "versionId"),
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "MetricSpec", response = classOf[MetricSpecView]),
    new ApiResponse(code = 404, message = "Not found"),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def getSpecForModelVersion: Route = path("metricspec" / "modelversion" / LongNumber) { id =>
    get {
      completeF(monRepo.forModelVersion(id).map(_.map(fromMetricSpec)))
    }
  }

  @Path("/{specId}")
  @ApiOperation(value = "deleteMetricSpec", notes = "deleteMetricSpec", nickname = "deleteMetricSpec", httpMethod = "DELETE")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "specId", required = true, dataType = "string", paramType = "path", value = "specId")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "MetricSpec",  response = classOf[MetricSpecView]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def deleteSpec: Route = path("metricspec" / Segment) { id =>
    delete {
      completeF(monitoringService.delete(id).map(fromMetricSpec))
    }
  }

  def routes: Route = pathPrefix("monitoring") {
    createSpec ~ listSpecs ~ getSpec ~ getSpecForModelVersion ~ deleteSpec
  }
}

object MonitoringRequests {
  final case class MetricSpecConfigCreationRequest(modelVersionId: Long, threshold: Double, thresholdCmpOperator: ThresholdCmpOperator)
  final case class MetricSpecCreationRequest(name: String, modelVersionId: Long, config: MetricSpecConfigCreationRequest)

  final case class MetricSpecConfigView(
    modelVersionId: Long,
    threshold: Double,
    thresholdCmpOperator: ThresholdCmpOperator,
    servable: Option[ServableView]
  )
  final case class MetricSpecView(  name: String,
    modelVersionId: Long,
    config: MetricSpecConfigView,
    id: String
  )

  def fromMetricSpec(res: CustomModelMetricSpec): MetricSpecView = {
    MetricSpecView(
      name = res.name,
      modelVersionId = res.modelVersionId,
      id = res.id,
      config = MetricSpecConfigView(
        modelVersionId = res.config.modelVersionId,
        threshold = res.config.threshold,
        thresholdCmpOperator = res.config.thresholdCmpOperator,
        servable = res.config.servable.map(ServableView.fromServable)
      )
    )
  }
}