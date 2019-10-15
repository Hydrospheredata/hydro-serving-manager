package io.hydrosphere.serving.manager.api.grpc

import cats.effect.Effect
import cats.effect.implicits._
import cats.implicits._
import com.google.protobuf.empty.Empty
import io.grpc.stub.StreamObserver
import io.hydrosphere.serving.manager.api.DeployServableRequest.ModelVersion
import io.hydrosphere.serving.manager.api.ManagerServiceGrpc.ManagerService
import io.hydrosphere.serving.manager.api.{DeployServableRequest, GetServablesRequest, GetVersionRequest, RemoveServableRequest}
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.model_version.ModelVersionService
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableService}
import io.hydrosphere.serving.manager.grpc
import io.hydrosphere.serving.manager.grpc.entities.{Servable => GServable}
import io.hydrosphere.serving.manager.util.grpc.Converters

import scala.concurrent.Future

class ManagerGrpcService[F[_]](
  versionService: ModelVersionService[F],
  servableService: ServableService[F]
)(implicit F: Effect[F]) extends ManagerService {

  override def getAllVersions(request: Empty, responseObserver: StreamObserver[grpc.entities.ModelVersion]): Unit = {
    val fAction = versionService.all().map { versions =>
      versions.foreach { v =>
        responseObserver.onNext(Converters.fromModelVersion(v))
      }
      responseObserver.onCompleted()
    }.onError {
      case exception =>
        Effect[F].delay {
          responseObserver.onError(exception)
        }
    }

    fAction.toIO.unsafeRunAsyncAndForget()
  }

  override def getVersion(request: GetVersionRequest): Future[grpc.entities.ModelVersion] = {
    val f = for {
      version <- versionService.get(request.id)
    } yield Converters.fromModelVersion(version)
    f.toIO.unsafeToFuture()
  }

  override def deployServable(request: DeployServableRequest, responseObserver: StreamObserver[GServable]): Unit = {
    val flow = for {
      res <- request.modelVersion match {
        case ModelVersion.Empty =>  F.raiseError[Servable](DomainError.invalidRequest("model version is not specified"))
        case ModelVersion.VersionId(value) => servableService.findAndDeploy(value, request.metadata)
        case ModelVersion.Fullname(value) => servableService.findAndDeploy(value.name, value.version, request.metadata) 
      }
      _ <- F.delay(responseObserver.onNext(Converters.fromServable(res)))
      _ <- F.delay(responseObserver.onCompleted())
    } yield ()

    flow
      .onError { case x => F.delay(responseObserver.onError(x)) }
      .toIO.unsafeRunAsyncAndForget()
  }

  override def removeServable(request: RemoveServableRequest): Future[GServable] = {
    val flow = for {
      res <- servableService.stop(request.servableName)
    } yield Converters.fromServable(res)
    flow.toIO.unsafeToFuture()
  }

  override def getServables(request: GetServablesRequest, responseObserver: StreamObserver[GServable]): Unit = {
    val flow = for {
        servables <- request.filter match {
        case Some(filter) => servableService.getFiltered(name=filter.name, versionId=filter.versionId, metadata=filter.metadata) // return filtered
        case None => servableService.all()
       }
       _ <- F.delay{ 
         servables.foreach{ s =>
          responseObserver.onNext(Converters.fromServable(s))
        }
      }
      _ <- F.delay(responseObserver.onCompleted())
    } yield ()

    flow
    .onError { case x => F.delay(responseObserver.onError(x)) }
    .toIO.unsafeRunAsyncAndForget()
  }
}