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
import org.apache.logging.log4j.scala.Logging


class MonitoringController[F[_]](
  monitoringService: Monitoring[F],
  monRepo: MonitoringRepository[F]
)(implicit F: Effect[F], uuid: UUIDGenerator[F]) extends AkkaHttpControllerDsl with Logging {
  implicit val configReq = jsonFormat3(MetricSpecConfigCreationRequest)
  implicit val specReq = jsonFormat3(MetricSpecCreationRequest)
  implicit val configView = jsonFormat4(MetricSpecConfigView)
  implicit val specView = jsonFormat4(MetricSpecView)

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

  def listSpecs: Route = path("metricspec") {
    get {
      completeF(monitoringService.all().map(_.map(fromMetricSpec)))
    }
  }

  def getSpec: Route = path("metricspec" / Segment) { id =>
    get {
      val flow = OptionT(monRepo.get(id))
        .map(fromMetricSpec)
        .getOrElseF(DomainError.notFound(s"Can't find metricspec id=$id").raiseError[F, MetricSpecView])
      completeF(flow)
    }
  }

  def getSpecForModelVersion: Route = path("metricspec" / "modelversion" / LongNumber) { id =>
    get {
      completeF(monRepo.forModelVersion(id).map(_.map(fromMetricSpec)))
    }
  }

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