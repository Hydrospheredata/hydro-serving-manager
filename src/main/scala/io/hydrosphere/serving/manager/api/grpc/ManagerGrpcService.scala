package io.hydrosphere.serving.manager.api.grpc

import cats.effect.implicits._
import cats.effect.kernel.{Async, Sync}
import cats.effect.std.Dispatcher
import cats.implicits._
import com.google.protobuf.empty.Empty
import io.grpc.stub.StreamObserver
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.model_version.ModelVersionService
import io.hydrosphere.serving.manager.domain.servable.Servable
import io.hydrosphere.serving.manager.domain.servable.ServableService
import io.hydrosphere.serving.manager.util.grpc.Converters

import scala.concurrent.Future
import io.hydrosphere.serving.proto.manager.api.DeployServableRequest.ModelVersion
import io.hydrosphere.serving.proto.manager.api.ManagerServiceGrpc.ManagerService
import io.hydrosphere.serving.proto.manager.{entities => grpcEntity}
import io.hydrosphere.serving.proto.manager.api.{
  DeployServableRequest,
  GetServablesRequest,
  GetVersionRequest,
  RemoveServableRequest
}
import org.apache.logging.log4j.scala.Logging

class ManagerGrpcService[F[_]](
    versionService: ModelVersionService[F],
    servableService: ServableService[F],
    dispatcher: Dispatcher[F]
)(implicit F: Sync[F])
    extends ManagerService
    with Logging {

  def nonEmptyString(str: String): Option[String] =
    if (str.trim.nonEmpty) Some(str) else None

  override def getAllVersions(
      request: Empty,
      responseObserver: StreamObserver[grpcEntity.ModelVersion]
  ): Unit = {
    val fAction = versionService
      .all()
      .map { versions =>
        versions.foreach(v => responseObserver.onNext(Converters.fromModelVersion(v)))
        responseObserver.onCompleted()
      }
      .onError {
        case exception =>
          F.delay {
            logger.debug(exception)
            responseObserver.onError(exception)
          }
      }

    dispatcher.unsafeRunAndForget(fAction)
  }

  override def getVersion(request: GetVersionRequest): Future[grpcEntity.ModelVersion] =
    dispatcher.unsafeToFuture {
      for {
        version <- versionService.get(request.id)
      } yield Converters.fromModelVersion(version)
    }

  override def deployServable(
      request: DeployServableRequest,
      responseObserver: StreamObserver[grpcEntity.Servable]
  ): Unit = {
    val flow = for {
      res <- request.modelVersion match {
        case ModelVersion.Empty =>
          F.raiseError[Servable](
            DomainError.invalidRequest("model version is not specified")
          )
        case ModelVersion.VersionId(value) =>
          servableService
            .findAndDeploy(value, nonEmptyString(request.deploymentConfigName), request.metadata)
        case ModelVersion.Fullname(value) =>
          servableService.findAndDeploy(
            value.name,
            value.version,
            nonEmptyString(request.deploymentConfigName),
            request.metadata
          )
      }
      _ <- F.delay(responseObserver.onNext(Converters.fromServable(res)))
      _ <- F.delay(responseObserver.onCompleted())
    } yield ()

    dispatcher.unsafeRunAndForget {
      flow.onError { case x => F.delay(responseObserver.onError(x)) }
    }
  }

  override def removeServable(request: RemoveServableRequest): Future[grpcEntity.Servable] =
    dispatcher.unsafeToFuture {
      for {
        res <- servableService.stop(request.servableName)
      } yield Converters.fromServable(res)
    }

  override def getServables(
      request: GetServablesRequest,
      responseObserver: StreamObserver[grpcEntity.Servable]
  ): Unit = {
    val flow = for {
      servables <- request.filter match {
        case Some(filter) =>
          servableService.getFiltered(
            name = filter.name,
            versionId = filter.versionId,
            metadata = filter.metadata
          ) // return filtered
        case None => servableService.all()
      }
      _ <- F.delay {
        servables.foreach(s => responseObserver.onNext(Converters.fromServable(s)))
      }
      _ <- F.delay(responseObserver.onCompleted())
    } yield ()

    dispatcher.unsafeRunAndForget {
      flow
        .onError { case x => F.delay(responseObserver.onError(x)) }
    }
  }
}

object ManagerGrpcService {
  def make[F[_]](
      versionService: ModelVersionService[F],
      servableService: ServableService[F]
  )(implicit F: Async[F]): F[ManagerGrpcService[F]] =
    Dispatcher[F].use { dispatcher =>
      F.pure {
        new ManagerGrpcService[F](versionService, servableService, dispatcher)
      }
    }
}
