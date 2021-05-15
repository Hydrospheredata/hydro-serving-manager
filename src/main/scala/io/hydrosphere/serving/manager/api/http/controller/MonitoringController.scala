package io.hydrosphere.serving.manager.api.http.controller

import akka.http.scaladsl.server.Route
import cats.MonadError
import cats.data.OptionT
import cats.effect.Async
import cats.effect.std.Dispatcher
import cats.implicits._
import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.api.http.controller.MonitoringController.MonitoringRequests._
import io.hydrosphere.serving.manager.api.http.controller.servable.ServableView
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.monitoring._
import org.apache.logging.log4j.scala.Logging

import javax.ws.rs.Path

@Path("/monitoring/metricspec")
class MonitoringController[F[_]](
    monitoringService: Monitoring[F],
    monRepo: MonitoringRepository[F]
)(implicit F: MonadError[F, Throwable], dispatcher: Dispatcher[F])
    extends AkkaHttpControllerDsl
    with Logging {
  @Path("/")
  def createSpec: Route =
    path("metricspec") {
      post {
        entity(as[MetricSpecCreationRequest]) { incomingMS =>
          completeF {
            monitoringService.create(incomingMS).map(fromMetricSpec)
          }
        }
      }
    }

  @Path("/")
  def listSpecs: Route =
    path("metricspec") {
      get {
        completeF(monitoringService.all().map(_.map(fromMetricSpec)))
      }
    }

  @Path("/{specId}")
  def getSpec: Route =
    path("metricspec" / Segment) { id =>
      get {
        val flow = OptionT(monRepo.get(id))
          .map(fromMetricSpec)
          .getOrElseF(
            DomainError.notFound(s"Can't find metricspec id=$id").raiseError[F, MetricSpecView]
          )
        completeF(flow)
      }
    }

  @Path("/modelversion/{versionId}")
  def getSpecForModelVersion: Route =
    path("metricspec" / "modelversion" / LongNumber) { id =>
      get {
        completeF(monRepo.forModelVersion(id).map(_.map(fromMetricSpec)))
      }
    }

  @Path("/{specId}")
  def deleteSpec: Route =
    path("metricspec" / Segment) { id =>
      delete {
        completeF(monitoringService.delete(id).map(fromMetricSpec))
      }
    }

  def routes: Route =
    pathPrefix("monitoring") {
      createSpec ~ listSpecs ~ getSpec ~ getSpecForModelVersion ~ deleteSpec
    }
}

object MonitoringController {
  def make[F[_]](
      monitoringService: Monitoring[F],
      monRepo: MonitoringRepository[F]
  )(implicit F: Async[F]) =
    Dispatcher[F].map(implicit disp => new MonitoringController[F](monitoringService, monRepo))

  object MonitoringRequests {
    @JsonCodec
    final case class MetricSpecConfigCreationRequest(
        modelVersionId: Long,
        threshold: Double,
        thresholdCmpOperator: ThresholdCmpOperator,
        deploymentConfigName: Option[String]
    )
    @JsonCodec
    final case class MetricSpecCreationRequest(
        name: String,
        modelVersionId: Long,
        config: MetricSpecConfigCreationRequest
    )

    @JsonCodec
    final case class MetricSpecConfigView(
        modelVersionId: Long,
        threshold: Double,
        thresholdCmpOperator: ThresholdCmpOperator,
        servable: Option[ServableView]
    )
    @JsonCodec
    final case class MetricSpecView(
        name: String,
        modelVersionId: Long,
        config: MetricSpecConfigView,
        id: String
    )

    def fromMetricSpec(res: CustomModelMetricSpec): MetricSpecView =
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
