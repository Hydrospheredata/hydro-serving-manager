package io.hydrosphere.serving.manager.api.http.controller

import akka.http.scaladsl.server.Route
import cats.data.OptionT
import cats.effect.Effect
import cats.implicits._
import io.hydrosphere.serving.manager.api.http.controller.MonitoringRequests._
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.monitoring.{CustomModelMetricSpec, CustomModelMetricSpecConfiguration, Monitoring, MonitoringRepository, ThresholdCmpOperator}
import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol._
import io.hydrosphere.serving.manager.util.UUIDGenerator
import org.apache.logging.log4j.scala.Logging


class MonitoringController[F[_]](
  monitoringService: Monitoring[F],
  monRepo: MonitoringRepository[F]
)(implicit F: Effect[F], uuid: UUIDGenerator[F]) extends AkkaHttpControllerDsl with Logging {
  implicit val m1 = jsonFormat3(MetricSpecConfigCreationRequest)
  implicit val m2 = jsonFormat3(MetricSpecCreationRequest)

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
            id = id.toString()
          )
          res <- monitoringService.create(ms)
        } yield res
        completeF(flow)
      }
    }
  }

  def listSpecs: Route = path("metricspec") {
    get {
      completeF(monitoringService.all())
    }
  }

  def getSpec: Route = path("metricspec" / Segment) { id =>
    get {
      val flow = OptionT(monRepo.get(id))
        .getOrElseF(DomainError.notFound(s"Can't find metricspec id=$id").raiseError[F, CustomModelMetricSpec])
      completeF(flow)
    }
  }

  def getSpecForModelVersion: Route = path("metricspec" / "modelversion" / LongNumber) { id =>
    get {
      completeF(monRepo.forModelVersion(id))
    }
  }

  def deleteSpec: Route = path("metricspec" / Segment) { id =>
    delete {
      completeF(monitoringService.delete(id).map(_ => "ok"))
    }
  }

  def routes: Route = pathPrefix("monitoring") {
    createSpec ~ listSpecs ~ getSpec ~ getSpecForModelVersion ~ deleteSpec
  }
}

object MonitoringRequests {
  final case class MetricSpecConfigCreationRequest(modelVersionId: Long, threshold: Double, thresholdCmpOperator: ThresholdCmpOperator)
  final case class MetricSpecCreationRequest(name: String, modelVersionId: Long, config: MetricSpecConfigCreationRequest)
}