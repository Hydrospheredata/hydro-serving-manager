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
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.application._
import io.hydrosphere.serving.manager.domain.deploy_config.DeploymentConfiguration
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.hydrosphere.serving.manager.domain.servable.Servable.GenericServable
import io.hydrosphere.serving.manager.infrastructure.protocol.CompleteJsonProtocol._
import io.hydrosphere.serving.manager.util.CollectionOps._
import spray.json._

import scala.util.Try

final case class DBGraphServable(
  modelVersionId: Long,
  weight: Int,
  servableName: Option[String],
  requiredDeployConfig: Option[String]
)
final case class DBGraphStage(variants: NonEmptyList[DBGraphServable], signature: ModelSignature)
final case class DBGraph(stages: NonEmptyList[DBGraphStage])
object DBGraph {
  implicit val format1 = jsonFormat4(DBGraphServable.apply)
  implicit val format2 = jsonFormat2(DBGraphStage.apply)
  implicit val format3 = jsonFormat1(DBGraph.apply)

  def assembleGraph(
    dbGraph: DBGraph,
    versions: Map[Long, ModelVersion.Internal],
    servables: Map[String, GenericServable],
    deploymentConfigs: Map[String, DeploymentConfiguration]
  ) = {
    val stages = dbGraph.stages.traverse { stage =>
      val variants = stage.variants.traverse { variant =>
        for {
          mv <- versions.get(variant.modelVersionId).toRight(DomainError.InternalError(s"Model version (${variant.modelVersionId}) is not found"))
          servable <- variant.servableName.traverse(x => servables.get(x).toRight(DomainError.InternalError(s"Servable (${x}) is not found")))
          depConf <- variant.requiredDeployConfig.traverse(x => deploymentConfigs.get(x).toRight(DomainError.InternalError(s"Deployment Config (${x}) is not found")))
        } yield ApplicationServable(mv, variant.weight, servable, depConf)
      }
      variants.map(ApplicationStage(_, stage.signature))
    }
    stages.map(ApplicationGraph.apply)
  }

  def disassembleGraph(graph: ApplicationGraph): DBGraph = {
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
}

object DBApplicationRepository {

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
    servables: Map[String, GenericServable],
    deploymentConfigs: Map[String, DeploymentConfiguration]
  ): Either[Throwable, Application] = {
    for {
      dbGraph <- Try(ar.execution_graph.parseJson.convertTo[DBGraph]).toEither
      graph <- DBGraph.assembleGraph(dbGraph, versions, servables, deploymentConfigs)
      status <- Application.Status.fromString(ar.status).toRight(DomainError.InternalError(s"Invalid application status: ${ar.status}"))
    } yield
      Application(
        id = ar.id,
        name = ar.application_name,
        signature = ModelSignature.fromAscii(ar.application_contract),
        kafkaStreaming = ar.kafka_streams.map(p => p.parseJson.convertTo[ApplicationKafkaStream]),
        namespace = ar.namespace,
        status = status,
        statusMessage = ar.status_message,
        graph = graph,
        metadata = ar.metadata.map(_.parseJson.convertTo[Map[String, String]]).getOrElse(Map.empty)
      )
  }

  def fromApplication(app: Application): ApplicationRow = {
    val graph = DBGraph.disassembleGraph(app.graph)
    val servables = graph.stages.toList.flatMap(_.variants.toList.flatMap(_.servableName))
    val versions = graph.stages.flatMap(_.variants.map(_.modelVersionId)).toList
    ApplicationRow(
      id = app.id,
      application_name = app.name,
      namespace = app.namespace,
      status = app.status.productPrefix,
      application_contract = app.signature.toProtoString,
      execution_graph = graph.toJson.compactPrint,
      used_servables = servables,
      used_model_versions = versions,
      kafka_streams = app.kafkaStreaming.map(_.toJson.compactPrint),
      status_message = app.statusMessage,
      metadata = app.metadata.maybeEmpty.map(_.toJson.compactPrint)
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

  def createQ(app:ApplicationRow) =
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


  def make[F[_]]()(implicit F: Bracket[F, Throwable], tx: Transactor[F], appPublisher: ApplicationEvents.Publisher[F]): ApplicationRepository[F] = new ApplicationRepository[F] {
    override def create(entity: Application): F[Application] = {
      val row = fromApplication(entity)
      createQ(row)
        .withUniqueGeneratedKeys[Long]("id")
        .transact(tx)
        .map(id => entity.copy(id = id))
        .flatTap(appPublisher.update)
    }

    override def get(id: Long): F[Option[Application]] = {
      val transaction = for {
        app <- OptionT(getByIdQ(id).option)
        appInfo <- OptionT.liftF(fetchAppInfo(app))
        (versions, servables, deploymentMap) = appInfo
      } yield (app, versions, servables, deploymentMap)
      val result = transaction.transact(tx).flatMap { case (app, versions, servables, deploymentMap) =>
        OptionT.liftF(F.fromEither(toApplication(app, versions, servables, deploymentMap)))
      }
      result.value
    }

    override def get(name: String): F[Option[Application]] = {
      val transaction = for {
        app <- OptionT(getByNameQ(name).option)
        appInfo <- OptionT.liftF(fetchAppInfo(app))
        (versions, servables, deploymentMap) = appInfo
      } yield (app, versions, servables, deploymentMap)
      val result = transaction.transact(tx).flatMap { case (app, versions, servables, deploymentMap) =>
        OptionT.liftF(F.fromEither(toApplication(app, versions, servables, deploymentMap)))
      }
      result.value
    }

    override def update(value: Application): F[Int] = {
      val row = fromApplication(value)
      updateQ(row).run.transact(tx)
        .flatTap(_ => appPublisher.update(value))
    }

    override def delete(id: Long): F[Int] = {
      get(id).flatMap {
        case Some(app) =>
          deleteQ(app.id).run.transact(tx)
            .flatTap(_ => appPublisher.remove(app.name))
        case None =>
          F.raiseError(DomainError.notFound(s"Application with id ${id} not found"))
      }
    }

    override def all(): F[List[Application]] = {
      val t = for {
        apps <- allQ.to[List]
        info <- fetchAppsInfo(apps)
        (versionMap, servableMap, deploymentMap) = info
        res = apps.traverse { app =>
          toApplication(app, versionMap, servableMap, deploymentMap)
        }
      } yield res
      t.transact(tx).map(_.leftWiden[Throwable]).rethrow
    }

    override def findVersionUsage(versionId: Long): F[List[Application]] = {
      val t = for {
        apps <- modelVersionUsageQ(versionId).to[List]
        info <- fetchAppsInfo(apps)
        (versionMap, servableMap, deploymentMap) = info
        res = apps.traverse { app =>
          toApplication(app, versionMap, servableMap, deploymentMap)
        }
      } yield res
      t.transact(tx).map(_.leftWiden[Throwable]).rethrow
    }

    override def findServableUsage(servableName: String): F[List[Application]] = {
      val t = for {
        apps <- servableUsageQ(servableName).to[List]
        info <- fetchAppsInfo(apps)
        (versionMap, servableMap, deploymentMap) = info
        res = apps.traverse { app =>
          toApplication(app, versionMap, servableMap, deploymentMap)
        }
      } yield res
      t.transact(tx).map(_.leftWiden[Throwable]).rethrow
    }
  }

  type AppInfo = (Map[Long, ModelVersion.Internal], Map[String, Servable[Servable.Status]], Map[String, DeploymentConfiguration])

  def fetchAppInfo(app: ApplicationRow): Free[connection.ConnectionOp, AppInfo] = fetchAppsInfo(NonEmptyList.of(app))

  def fetchAppsInfo(app: List[ApplicationRow]): Free[connection.ConnectionOp, AppInfo] = {
    NonEmptyList.fromList(app).fold(Free.pure[connection.ConnectionOp, AppInfo]((Map.empty, Map.empty, Map.empty)))(fetchAppsInfo)
  }

  def fetchAppsInfo(apps: NonEmptyList[ApplicationRow]): Free[connection.ConnectionOp, AppInfo] = {
    val graphs = apps.map(_.execution_graph.parseJson.convertTo[DBGraph])
    val nodes = graphs.flatMap(_.stages.flatMap(_.variants))
    for {
     versions <- {
       DBModelVersionRepository.findVersionsQ(nodes.map(_.modelVersionId))
         .to[List]
         .map { list =>
           list
             .map(DBModelVersionRepository.toModelVersionT)
             .collect {
               case x: ModelVersion.Internal => x
             }
         }
     }
     versionMap = versions.map(x => x.id -> x).toMap
     servables <- {
       val allServables = nodes.toList.flatMap(_.servableName)
       NonEmptyList.fromList(allServables) match {
         case Some(x) =>
           DBServableRepository.getManyQ(x)
             .to[List]
             .map(list => list.map(x => DBServableRepository.toServableT(x)).collect{
               case Right(x) => x
             })
         case None => Nil.pure[ConnectionIO]
       }
     }
     servableMap = servables.map(x => x.fullName -> x).toMap
     deployments <- {
       val allDeps = nodes.toList.flatMap(_.requiredDeployConfig)
       NonEmptyList.fromList(allDeps) match {
         case Some(x) =>
           DBDeploymentConfigurationRepository.getManyQ(x)
             .to[List]
         case None => Nil.pure[ConnectionIO]
       }
     }
      deploymentMap = deployments.map(x => x.name -> x).toMap
   } yield (versionMap, servableMap, deploymentMap)
  }

}