package io.hydrosphere.serving.manager.infrastructure.db.repository

import cats.data.OptionT
import cats.implicits._
import cats.effect.Bracket
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol._
import spray.json._
import io.hydrosphere.serving.manager.domain.monitoring.{CustomModelMetricSpec, CustomModelMetricSpecConfiguration, MonitoringRepository, ThresholdCmpOperator}

import scala.util.Try

object DBMonitoringRepository {

  case class UnknownMetricSpec(row: MetricSpecRow) extends RuntimeException(s"Unknown MetricSpec id=${row.id} name=${row.name} kind=${row.kind}")

  case class MetricSpecRow(
    id: String,
    kind: String,
    name: String,
    modelVersionId: Long,
    config: String,
  )

  case class CustomModelConfigRow(
    modelVersionId: Long,
    thresholdValue: Option[Double],
    thresholdOp: Option[ThresholdCmpOperator],
    servableName: Option[String]
  )

  def parseConfig(row: MetricSpecRow) = {
    row.kind match {
      case CustomModelMetricSpec.getClass.getSimpleName =>
        Try(row.config.parseJson.convertTo[CustomModelConfigRow]).toEither
      case _ => Left(UnknownMetricSpec(row))
    }
  }

  def toRowConfig(spec: CustomModelMetricSpec) = {
    val config = CustomModelConfigRow(
      modelVersionId = spec.config.modelVersionId,
      thresholdValue = spec.config.threshold,
      thresholdOp = spec.config.thresholdCmpOperator,
      servableName = spec.config.servable.map(_.fullName)
    )
    Try(config.toJson.compactPrint).toEither.map { json =>
      MetricSpecRow(
        id = spec.id,
        kind = spec.productPrefix,
        name = spec.name,
        modelVersionId = spec.modelVersionId,
        config = json
      )
    }
  }

  def upsertQ(metricSpec: MetricSpecRow) =
    sql"""
         INSERT INTO hydro_serving.metric_specs(kind, name, modelVersionId, config, withHealth, id)
         VALUES (${metricSpec.kind}, ${metricSpec.name}, ${metricSpec.modelVersionId}, ${metricSpec.config.toJson}, ${metricSpec.withHealth}, ${metricSpec.id})
         ON CONFLICT (id) DO UPDATE
         SET kind = ${metricSpec.kind},
             name = ${metricSpec.name},
             modelVersionId = ${metricSpec.modelVersionId},
             config = ${metricSpec.config.toJson},
       """.update

  def selectByIdQ(specId: String) =
    sql"""
         SELECT kind, name, modelVersionId, config, withHealth, id
         FROM hydro_serving.metric_specs
         WHERE id = $specId
       """.query[MetricSpecRow]

  def selectByVersionId(modelVersionId: Long) =
    sql"""
           SELECT kind, name, modelVersionId, config, withHealth, id
           FROM hydro_serving.metric_specs
           WHERE modelVersionId = $modelVersionId
         """.query[MetricSpecRow]

  final val allQ =
    sql"""
           SELECT kind, name, modelVersionId, config, withHealth, id
           FROM hydro_serving.metric_specs
         """.query[MetricSpecRow]

  def deleteQ(specId: String) =
    sql"""
           DELETE FROM hydro_serving.metric_specs WHERE id = $specId
         """.update

  def make[F[_]]()(implicit F: Bracket[F, Throwable], tx: Transactor[F]): MonitoringRepository[F] = new MonitoringRepository[F]() {
    override def all(): F[List[CustomModelMetricSpec]] = {
      for {
        rawSpecs <- allQ.to[List].transact(tx)
        parsedSpecs <- rawSpecs.traverse(getFullMetricSpec)
      } yield parsedSpecs
    }

    override def get(id: String): F[Option[CustomModelMetricSpec]] = {
      val flow = for {
        raw <- OptionT(selectByIdQ(id).option.transact(tx))
        res <- OptionT.liftF(getFullMetricSpec(raw))
      } yield res
      flow.value
    }

    override def insert(spec: CustomModelMetricSpec): F[Unit] = {
      for {
        row <- F.fromEither(toRowConfig(spec))
        _ <- upsertQ(row).run.transact(tx)
      } yield ()
    }

    override def delete(id: String): F[Unit] = {
      deleteQ(id).run.transact(tx).void
    }

    def getFullMetricSpec(rawSpec: MetricSpecRow) = {
      for {
        parsedConfig <- F.fromEither(parseConfig(rawSpec))
        servableRow <- parsedConfig.servableName.flatTraverse { servableName =>
          DBServableRepository.getQ(servableName).option.transact(tx)
        }
        servable = servableRow.map(DBServableRepository.toServableT)
      } yield {
        val config = CustomModelMetricSpecConfiguration(
          modelVersionId = parsedConfig.modelVersionId,
          threshold = parsedConfig.thresholdValue,
          thresholdCmpOperator = parsedConfig.thresholdOp,
          servable = servable
        )
        CustomModelMetricSpec(
          name = rawSpec.name,
          modelVersionId = rawSpec.modelVersionId,
          config = config,
          id = rawSpec.id
        )
      }
    }
  }
}