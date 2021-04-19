package io.hydrosphere.serving.manager.infrastructure.db.repository

import cats.data.OptionT
import cats.implicits._
import cats.effect.Bracket
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.domain.monitoring._
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBServableRepository.JoinedServableRow

import scala.util.Try
import io.circe.parser._
import io.circe.syntax._

object DBMonitoringRepository {

  case class InvalidMetricSpecConfig(row: MetricSpecRow)
      extends RuntimeException(
        s"Invalid config for MetricSpec id=${row.id} name=${row.name} kind=${row.kind} config=${row.config}"
      )

  @JsonCodec
  case class MetricSpecRow(
      kind: String,
      name: String,
      modelVersionId: Long,
      config: Option[String],
      id: String
  )

  @JsonCodec
  case class CustomModelConfigRow(
      modelVersionId: Long,
      thresholdValue: Double,
      thresholdOp: ThresholdCmpOperator,
      servableName: Option[String],
      deploymentConfigName: Option[String]
  )

  def parseConfig(row: MetricSpecRow) =
    row.kind match {
      case "CustomModelMetricSpec" =>
        for {
          config       <- row.config.toRight(InvalidMetricSpecConfig(row))
          parsedConfig <- decode[CustomModelConfigRow](config)
        } yield parsedConfig
      case _ => Left(InvalidMetricSpecConfig(row))
    }

  def toRowConfig(spec: CustomModelMetricSpec) = {
    val config = CustomModelConfigRow(
      modelVersionId = spec.config.modelVersionId,
      thresholdValue = spec.config.threshold,
      thresholdOp = spec.config.thresholdCmpOperator,
      servableName = spec.config.servable.map(_.name),
      deploymentConfigName = spec.config.deploymentConfigName
    )
    Try(config.asJson.noSpaces).toEither.map { json =>
      MetricSpecRow(
        id = spec.id,
        kind = spec.productPrefix,
        name = spec.name,
        modelVersionId = spec.modelVersionId,
        config = json.some
      )
    }
  }

  def upsertQ(metricSpec: MetricSpecRow) =
    sql"""
         INSERT INTO hydro_serving.metric_specs(kind, name, modelVersionId, config, id)
         VALUES (${metricSpec.kind}, ${metricSpec.name}, ${metricSpec.modelVersionId}, ${metricSpec.config}, ${metricSpec.id})
         ON CONFLICT (id) DO UPDATE
         SET kind = ${metricSpec.kind},
             name = ${metricSpec.name},
             modelVersionId = ${metricSpec.modelVersionId},
             config = ${metricSpec.config}
       """.update

  def selectByIdQ(specId: String) =
    sql"""
         SELECT kind, name, modelVersionId, config, id
         FROM hydro_serving.metric_specs
         WHERE id = $specId
       """.query[MetricSpecRow]

  def selectByVersionIdQ(modelVersionId: Long) =
    sql"""
           SELECT kind, name, modelVersionId, config, id
           FROM hydro_serving.metric_specs
           WHERE modelVersionId = $modelVersionId
         """.query[MetricSpecRow]

  final val allQ =
    sql"""
           SELECT kind, name, modelVersionId, config, id
           FROM hydro_serving.metric_specs
         """.query[MetricSpecRow]

  def deleteQ(specId: String) =
    sql"""
           DELETE FROM hydro_serving.metric_specs WHERE id = $specId
         """.update

  def make[F[_]]()(implicit
      F: Bracket[F, Throwable],
      tx: Transactor[F],
      pub: MetricSpecEvents.Publisher[F]
  ): MonitoringRepository[F] =
    new MonitoringRepository[F]() {
      override def all(): F[List[CustomModelMetricSpec]] =
        for {
          rawSpecs    <- allQ.to[List].transact(tx)
          parsedSpecs <- rawSpecs.traverse(getFullMetricSpec)
        } yield parsedSpecs

      override def get(id: String): F[Option[CustomModelMetricSpec]] = {
        val flow = for {
          raw <- OptionT(selectByIdQ(id).option.transact(tx))
          res <- OptionT.liftF[F, CustomModelMetricSpec](getFullMetricSpec(raw))
        } yield res
        flow.value
      }

      override def upsert(spec: CustomModelMetricSpec): F[Unit] = {
        val flow = for {
          row <- F.fromEither(toRowConfig(spec))
          _   <- upsertQ(row).run.transact(tx)
        } yield ()
        flow.flatTap(_ => pub.update(spec))
      }

      override def delete(id: String): F[Unit] =
        deleteQ(id).run
          .transact(tx)
          .void
          .flatTap(_ => pub.remove(id))

      override def forModelVersion(id: Long): F[List[CustomModelMetricSpec]] =
        for {
          raw <- selectByVersionIdQ(id).to[List].transact(tx)
          res <- raw.traverse(getFullMetricSpec)
        } yield res

      def getFullMetricSpec(rawSpec: MetricSpecRow): F[CustomModelMetricSpec] =
        for {
          parsedConfig <- F.fromEither(parseConfig(rawSpec))
          servableRow <- parsedConfig.servableName.flatTraverse[F, JoinedServableRow] {
            servableName => DBServableRepository.getQ(servableName).option.transact(tx)
          }
          servable <- servableRow.traverse(x => F.fromEither(DBServableRepository.toServableT(x)))
        } yield {
          val config = CustomModelMetricSpecConfiguration(
            modelVersionId = parsedConfig.modelVersionId,
            threshold = parsedConfig.thresholdValue,
            thresholdCmpOperator = parsedConfig.thresholdOp,
            servable = servable,
            deploymentConfigName = parsedConfig.deploymentConfigName
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
