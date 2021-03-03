package io.hydrosphere.serving.manager.infrastructure.db.repository

import cats.data.{NonEmptyList, OptionT}
import cats.effect.Bracket
import cats.free.Free
import cats.implicits._
import doobie._
import doobie.free.connection
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import io.circe
import io.circe.generic.JsonCodec

import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.application._
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfiguration
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.hydrosphere.serving.manager.util.CollectionOps._
import io.circe.parser._
import io.circe.syntax._

@JsonCodec
final case class DBGraphServable(
    modelVersionId: Long,
    weight: Int,
    servableName: Option[String],
    requiredDeployConfig: Option[String]
)
@JsonCodec
final case class DBGraphStage(variants: NonEmptyList[DBGraphServable], signature: Signature)
@JsonCodec
final case class DBGraph(stages: NonEmptyList[DBGraphStage])

object DBGraph {

  def assembleGraph(
      dbGraph: DBGraph,
      versions: Map[Long, ModelVersion.Internal],
      servables: Map[String, Servable],
      deploymentConfigs: Map[String, DeploymentConfiguration]
  ) = {
    val stages = dbGraph.stages.traverse { stage =>
      val variants = stage.variants.traverse { variant =>
        for {
          mv <-
            versions
              .get(variant.modelVersionId)
              .toRight(
                DomainError.InternalError(s"Model version (${variant.modelVersionId}) is not found")
              )
          servable <- variant.servableName.traverse(x =>
            servables.get(x).toRight(DomainError.InternalError(s"Servable (${x}) is not found"))
          )
          depConf <- variant.requiredDeployConfig.traverse(x =>
            deploymentConfigs
              .get(x)
              .toRight(DomainError.InternalError(s"Deployment Config (${x}) is not found"))
          )
        } yield ApplicationServable(mv, variant.weight, servable, depConf)
      }
      variants.map(ApplicationStage(_, stage.signature))
    }
    stages.map(ApplicationGraph.apply)
  }

  def disassembleGraph(graph: ApplicationGraph): DBGraph =
    DBGraph(
      stages = graph.stages.map { stage =>
        DBGraphStage(
          variants = stage.variants.map { variant =>
            DBGraphServable(
              modelVersionId = variant.modelVersion.id,
              weight = variant.weight,
              servableName = variant.servable.map(_.fullName),
              requiredDeployConfig = variant.requiredDeploymentConfig.map(_.name)
            )
          },
          signature = stage.signature
        )
      }
    )
}

object DBApplicationRepository {

  type AppInfo = (
      Map[Long, ModelVersion.Internal],
      Map[String, Servable],
      Map[String, DeploymentConfiguration]
  )

  @JsonCodec
  final case class ApplicationRow(
      id: Long,
      application_name: String,
      namespace: Option[String],
      status: String,
      application_contract: String,
      execution_graph: String,
      used_servables: List[String],
      kafka_streams: List[String],
      status_message: Option[String],
      used_model_versions: List[Long],
      metadata: Option[String]
  )

  def toApplication(
      ar: ApplicationRow,
      versions: Map[Long, ModelVersion.Internal],
      servables: Map[String, Servable],
      deploymentConfigs: Map[String, DeploymentConfiguration]
  ): Either[Throwable, Application] =
    for {
      dbGraph <- decode[DBGraph](ar.execution_graph)
      graph   <- DBGraph.assembleGraph(dbGraph, versions, servables, deploymentConfigs)
      kafkaStreaming <-
        ar.kafka_streams.traverse(p => parse(p).flatMap(_.as[ApplicationKafkaStream]))
      signature <- decode[Signature](ar.application_contract)
      metadata =
        ar.metadata.flatMap(m => decode[Map[String, String]](m).toOption).getOrElse(Map.empty)
      message = ar.status_message
      status =
        Application.Status.withNameInsensitiveOption(ar.status).getOrElse(Application.Status.Failed)
    } yield Application(
      id = ar.id,
      name = ar.application_name,
      signature = signature,
      kafkaStreaming = kafkaStreaming,
      namespace = ar.namespace,
      status = status,
      statusMessage = message,
      graph = graph,
      metadata = metadata
    )

  def fromApplication(app: Application): ApplicationRow = {
    val graph     = DBGraph.disassembleGraph(app.graph)
    val servables = graph.stages.toList.flatMap(_.variants.toList.flatMap(_.servableName))
    val versions  = graph.stages.flatMap(_.variants.map(_.modelVersionId)).toList
    ApplicationRow(
      id = app.id,
      application_name = app.name,
      namespace = app.namespace,
      status = app.status.entryName,
      application_contract = app.signature.asJson.noSpaces,
      execution_graph = graph.asJson.noSpaces,
      used_servables = servables,
      used_model_versions = versions,
      kafka_streams = app.kafkaStreaming.map(_.asJson.noSpaces),
      status_message = app.statusMessage,
      metadata = app.metadata.maybeEmpty.map(_.asJson.noSpaces)
    )
  }

  def allQ =
    sql"""
         |SELECT * FROM hydro_serving.application
        """.stripMargin.query[ApplicationRow]

  def getByNameQ(name: String) =
    sql"""
         |SELECT * FROM hydro_serving.application
         | WHERE application_name = $name
      """.stripMargin.query[ApplicationRow]

  def getByIdQ(id: Long) =
    sql"""
         |SELECT * FROM hydro_serving.application
         | WHERE id = $id
      """.stripMargin.query[ApplicationRow]

  def modelVersionUsageQ(versionId: Long) =
    sql"""
         |SELECT * FROM hydro_serving.application
         | WHERE ${versionId} = ANY(used_model_versions)
      """.stripMargin.query[ApplicationRow]

  def servableUsageQ(servableName: String) =
    sql"""
         |SELECT * FROM hydro_serving.application
         | WHERE ${servableName} = ANY(used_servables)
      """.stripMargin.query[ApplicationRow]

  def createQ(app: ApplicationRow) =
    sql"""
         |INSERT INTO hydro_serving.application(
         | application_name,
         | used_servables,
         | used_model_versions,
         | application_contract,
         | execution_graph,
         | kafka_streams,
         | status,
         | status_message
         |) VALUES (
         | ${app.application_name},
         | ${app.used_servables},
         | ${app.used_model_versions},
         | ${app.application_contract},
         | ${app.execution_graph},
         | ${app.kafka_streams},
         | ${app.status},
         | ${app.status_message}
         |)
      """.stripMargin.update

  def updateQ(app: ApplicationRow) =
    sql"""
         |UPDATE hydro_serving.application SET
         | application_name = ${app.application_name},
         | used_servables = ${app.used_servables},
         | used_model_versions = ${app.used_model_versions},
         | application_contract = ${app.application_contract},
         | execution_graph = ${app.execution_graph},
         | kafka_streams = ${app.kafka_streams},
         | status = ${app.status},
         | status_message = ${app.status_message}
         | WHERE id = ${app.id}
      """.stripMargin.update

  def deleteQ(id: Long) =
    sql"""
         |DELETE FROM hydro_serving.application
         | WHERE id = $id
      """.stripMargin.update

  def make[F[_]]()(implicit
      F: Bracket[F, Throwable],
      tx: Transactor[F],
      appPublisher: ApplicationEvents.Publisher[F]
  ): ApplicationRepository[F] =
    new ApplicationRepository[F] {
      override def create(entity: Application): F[Application] = {
        val row = fromApplication(entity)
        createQ(row)
          .withUniqueGeneratedKeys[Long]("id")
          .transact(tx)
          .map(id => entity.copy(id = id))
          .flatTap(appPublisher.update)
      }

      override def get(id: Long): F[Option[Application]] = {
        val res = for {
          app     <- OptionT(getByIdQ(id).option).transact(tx)
          appInfo <- OptionT.liftF(fetchAppInfo(app))
          (versions, servables, deploymentMap) = appInfo
        } yield (app, versions, servables, deploymentMap)

        val result = res.flatMap {
          case (app, versions, servables, deploymentMap) =>
            OptionT.liftF(F.fromEither(toApplication(app, versions, servables, deploymentMap)))
        }
        result.value
      }

      override def get(name: String): F[Option[Application]] = {
        val transaction = for {
          app     <- OptionT(getByNameQ(name).option).transact(tx)
          appInfo <- OptionT.liftF(fetchAppInfo(app))
          (versions, servables, deploymentMap) = appInfo
        } yield (app, versions, servables, deploymentMap)
        val result = transaction.flatMap {
          case (app, versions, servables, deploymentMap) =>
            OptionT.liftF(F.fromEither(toApplication(app, versions, servables, deploymentMap)))
        }
        result.value
      }

      override def update(value: Application): F[Int] = {
        val row = fromApplication(value)
        updateQ(row).run
          .transact(tx)
          .flatTap(_ => appPublisher.update(value))
      }

      override def delete(id: Long): F[Int] =
        get(id).flatMap {
          case Some(app) =>
            deleteQ(app.id).run
              .transact(tx)
              .flatTap(_ => appPublisher.remove(app.name))
          case None =>
            F.raiseError(DomainError.notFound(s"Application with id ${id} not found"))
        }

      override def all(): F[List[Application]] = {
        val t = for {
          apps <- allQ.to[List].transact(tx)
          info <- fetchAppsInfo(apps)
          (versionMap, servableMap, deploymentMap) = info
          res                                      = apps.traverse(app => toApplication(app, versionMap, servableMap, deploymentMap))
        } yield res
        t.map(_.leftWiden[Throwable]).rethrow
      }

      override def findVersionUsage(versionId: Long): F[List[Application]] = {
        val t = for {
          apps <- modelVersionUsageQ(versionId).to[List].transact(tx)
          info <- fetchAppsInfo(apps)
          (versionMap, servableMap, deploymentMap) = info
          res                                      = apps.traverse(app => toApplication(app, versionMap, servableMap, deploymentMap))
        } yield res
        t.map(_.leftWiden[Throwable]).rethrow
      }

      override def findServableUsage(servableName: String): F[List[Application]] = {
        val t = for {
          apps <- servableUsageQ(servableName).to[List].transact(tx)
          info <- fetchAppsInfo(apps)
          (versionMap, servableMap, deploymentMap) = info
          res                                      = apps.traverse(app => toApplication(app, versionMap, servableMap, deploymentMap))
        } yield res
        t.map(_.leftWiden[Throwable]).rethrow
      }

      def fetchAppInfo(app: ApplicationRow): F[AppInfo] = fetchAppsInfo(NonEmptyList.of(app))

      def fetchAppsInfo(app: List[ApplicationRow]): F[AppInfo] =
        NonEmptyList
          .fromList(app)
          .fold(F.pure[AppInfo](Map.empty, Map.empty, Map.empty))(fetchAppsInfo)

      // TODO: Servable name
      def fetchAppsInfo(apps: NonEmptyList[ApplicationRow]): F[AppInfo] = {
        val graphsOrError: Either[circe.Error, NonEmptyList[DBGraph]] =
          apps.traverse(ar => decode[DBGraph](ar.execution_graph))
        val nodesOrError: Either[circe.Error, NonEmptyList[DBGraphServable]] =
          graphsOrError.map(list => list.flatMap(ag => ag.stages.flatMap(_.variants)))

        for {
          ids <- F.fromEither(nodesOrError.map(n => n.map(_.modelVersionId)))
          versions <-
            DBModelVersionRepository
              .findVersionsQ(ids)
              .to[List]
              .transact(tx)
          internalVersions <-
            F.fromEither(versions.traverse(DBModelVersionRepository.toModelVersionT).map { list =>
              list.collect { case x: ModelVersion.Internal => x }
            })
          versionMap = internalVersions.map(v => v.id -> v).toMap
          servableNames <- F.fromEither(nodesOrError.map(n => n.map(x => x.servableName.get)))
          servables <-
            DBServableRepository
              .getManyQ(servableNames)
              .to[List]
              .map(list =>
                list.map(x => DBServableRepository.toServableT(x)).collect {
                  case Right(x) => x
                }
              )
              .transact(tx)
          servableMap = servables.map(x => x.fullName -> x).toMap
          deploymentNames <- F.fromEither(
            nodesOrError.map(n =>
              n.map(y =>
                y.requiredDeployConfig match {
                  case Some(dc) => dc
                }
              )
            )
          )
          deployments <-
            DBDeploymentConfigurationRepository
              .getManyQ(deploymentNames)
              .to[List]
              .transact(tx)
          deploymentMap = deployments.map(x => x.name -> x).toMap
        } yield (versionMap, servableMap, deploymentMap)
      }
    }
}
