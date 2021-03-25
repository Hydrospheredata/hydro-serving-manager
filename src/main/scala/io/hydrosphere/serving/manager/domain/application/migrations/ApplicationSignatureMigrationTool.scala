package io.hydrosphere.serving.manager.domain.application.migrations

import cats.effect.Bracket
import cats.implicits._
import doobie.util.transactor.Transactor
import io.hydrosphere.serving.manager.domain.application.{
  Application,
  ApplicationKafkaStream,
  ApplicationRepository,
  ApplicationServable,
  GraphComposer
}
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionRepository}
import io.hydrosphere.serving.manager.infrastructure.db.repository.{
  DBApplicationRepository,
  DBGraph
}
import doobie.implicits._
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBApplicationRepository.ApplicationRow
import io.circe.parser._
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.domain.deploy_config.{
  DeploymentConfiguration,
  DeploymentConfigurationRepository
}
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableRepository}
import org.apache.logging.log4j.scala.Logging

trait ApplicationSignatureMigrationTool[F[_]] {
  def getAndRecover(): F[Unit]
}

object ApplicationSignatureMigrationTool extends Logging {

  def default[F[_]](
      appsRepo: ApplicationRepository[F],
      modelVersionRepo: ModelVersionRepository[F],
      servableRepo: ServableRepository[F],
      deploymentRepo: DeploymentConfigurationRepository[F]
  )(implicit F: Bracket[F, Throwable], tx: Transactor[F]) =
    new ApplicationSignatureMigrationTool[F] {
      override def getAndRecover(): F[Unit] = {
        logger.info("Start recovering process...")
        for {
          apps <- DBApplicationRepository.allQ.to[List].transact(tx)
          appsNeedsToUpdate = apps.filter(ar => decode[Signature](ar.application_contract).isLeft)
          result <- appsNeedsToUpdate.traverse { appRow =>
            for {
              graph <- F.fromEither(decode[DBGraph](appRow.execution_graph))
              versions <-
                appRow.used_model_versions
                  .traverse(modelVersionRepo.get)
                  .map {
                    _.collect {
                      case Some(x: ModelVersion.Internal) => x.id -> x
                    }.toMap
                  }
              servables <-
                appRow.used_servables
                  .traverse(servableRepo.get)
                  .map(list =>
                    list.collect {
                      case Some(servable) => servable.fullName -> servable
                    }.toMap
                  )
              depNames = graph.stages.flatMap(_.variants).toList.flatMap(_.requiredDeployConfig)
              deployments <-
                depNames
                  .traverse(deploymentRepo.get)
                  .map(list =>
                    list.collect {
                      case Some(dc) => dc.name -> dc
                    }.toMap
                  )
              _ <- restoreApp(appRow, versions, servables, deployments) match {
                case Left(err) =>
                  F.raiseError[Unit](err)
                case Right(application) =>
                  logger.info(s"Application with id ${application.id} was restored")
                  appsRepo.update(application).void

              }
            } yield ()
          }.attempt
          _ <- result match {
            case Right(_) =>
              logger.info("Applications are ok.")
              F.unit
            case Left(err) =>
              logger.error("Unrecoverable error while reading Application from database", err)
              err.raiseError[F, Unit]
          }
        } yield ()
      }

      def restoreApp(
          ar: ApplicationRow,
          versions: Map[Long, ModelVersion.Internal],
          servables: Map[String, Servable],
          deploymentConfigs: Map[String, DeploymentConfiguration]
      ): Either[DomainError, Application] =
        for {
          dbGraph <- decode[DBGraph](ar.execution_graph).leftMap(_ =>
            DomainError.internalError("Couldn't decode execution graph")
          )
          versions <- dbGraph.stages.traverse { stage =>
            for {
              variants <- stage.variants.traverse { variant =>
                for {
                  version <- versions.get(variant.modelVersionId) match {
                    case Some(value) => value.asRight
                    case None =>
                      DomainError
                        .internalError("Couldn't find model version for model variant")
                        .asLeft
                  }
                  servable   = variant.servableName.flatMap(servables.get)
                  deployment = variant.requiredDeployConfig.flatMap(deploymentConfigs.get)
                } yield ApplicationServable(
                  modelVersion = version,
                  weight = variant.weight,
                  servable = servable,
                  requiredDeploymentConfig = deployment
                )
              }
            } yield variants
          }
          graphOrError <- GraphComposer.compose(versions)
          (graph, signature) = graphOrError
          kafkaStreams <- ar.kafka_streams.traverse(
            decode[ApplicationKafkaStream](_).leftMap(_ =>
              DomainError.internalError("Couldn't decode kafka stream")
            )
          )
        } yield Application(
          id = ar.id,
          name = ar.application_name,
          signature = signature,
          namespace = ar.namespace,
          status = Application.Status
            .withNameInsensitiveOption(ar.status)
            .getOrElse(Application.Status.Failed),
          statusMessage = ar.status_message,
          kafkaStreaming = kafkaStreams,
          graph = graph
        )
    }
}
