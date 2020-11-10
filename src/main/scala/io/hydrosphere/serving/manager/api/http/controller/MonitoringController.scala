package io.hydrosphere.serving.manager.api.http.controller

import akka.http.scaladsl.server.Route
import cats.data.OptionT
import cats.effect.Effect
import cats.implicits._
import io.hydrosphere.serving.manager.api.http.controller.MonitoringRequests._
import io.hydrosphere.serving.manager.api.http.controller.servable.ServableView
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.monitoring.{CustomModelMetricSpec, Monitoring, MonitoringRepository, ThresholdCmpOperator}
import javax.ws.rs.Path
import org.apache.logging.log4j.scala.Logging

@Path("/monitoring/metricspec")
class MonitoringController[F[_]](
  monitoringService: Monitoring[F],
  monRepo: MonitoringRepository[F]
)(implicit F: Effect[F]) extends AkkaHttpControllerDsl with Logging {
  implicit val configReq = jsonFormat4(MetricSpecConfigCreationRequest)
  implicit val specReq = jsonFormat3(MetricSpecCreationRequest)
  implicit val configView = jsonFormat4(MetricSpecConfigView)
  implicit val specView = jsonFormat4(MetricSpecView)

  @Path("/")
  def createSpec: Route = path("metricspec") {
    post {
      entity(as[MetricSpecCreationRequest]) { incomingMS =>
        completeF {
          monitoringService.create(incomingMS).map(fromMetricSpec)
        }
      }
    }
  }

  @Path("/")
  def listSpecs: Route = path("metricspec") {
    get {
      completeF(monitoringService.all().map(_.map(fromMetricSpec)))
    }
  }

  @Path("/{specId}")
  def getSpec: Route = path("metricspec" / Segment) { id =>
    get {
      val flow = OptionT(monRepo.get(id))
        .map(fromMetricSpec)
        .getOrElseF(DomainError.notFound(s"Can't find metricspec id=$id").raiseError[F, MetricSpecView])
      completeF(flow)
    }
  }

  @Path("/modelversion/{versionId}")
  def getSpecForModelVersion: Route = path("metricspec" / "modelversion" / LongNumber) { id =>
    get {
      completeF(monRepo.forModelVersion(id).map(_.map(fromMetricSpec)))
    }
  }

  @Path("/{specId}")
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
  final case class MetricSpecConfigCreationRequest(
    modelVersionId: Long,
    threshold: Double,
    thresholdCmpOperator: ThresholdCmpOperator,
    deploymentConfigName: Option[String]
  )
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