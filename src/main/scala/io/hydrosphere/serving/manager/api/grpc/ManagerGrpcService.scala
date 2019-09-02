package io.hydrosphere.serving.manager.api.grpc

import cats.data.EitherT
import cats.effect.Effect
import cats.effect.implicits._
import cats.implicits._
import com.google.protobuf.empty.Empty
import io.grpc.stub.StreamObserver
import io.hydrosphere.serving.manager.api.DeployServableRequest.ModelVersion
import io.hydrosphere.serving.manager.api.ManagerServiceGrpc.ManagerService
import io.hydrosphere.serving.manager.api.{DeployServableRequest, GetVersionRequest, RemoveServableRequest}
import io.hydrosphere.serving.manager.domain.DomainError
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersionRepository, ModelVersion => DMV}
import io.hydrosphere.serving.manager.domain.servable.Servable.GenericServable
import io.hydrosphere.serving.manager.domain.servable.ServableService
import io.hydrosphere.serving.manager.grpc
import io.hydrosphere.serving.manager.grpc.entities.Servable
import io.hydrosphere.serving.manager.util.DeferredResult
import io.hydrosphere.serving.manager.util.grpc.Converters

import scala.concurrent.Future
import io.hydrosphere.serving.manager.api.GetServablesRequest

class ManagerGrpcService[F[_]](
  versionRepo: ModelVersionRepository[F],
  servableService: ServableService[F]
)(implicit F: Effect[F]) extends ManagerService {

  override def getAllVersions(request: Empty, responseObserver: StreamObserver[grpc.entities.ModelVersion]): Unit = {
    val fAction = versionRepo.all().map { versions =>
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
      version <- EitherT.fromOptionF[F, Throwable, DMV](versionRepo.get(request.id), new IllegalArgumentException(s"ModelVersion with id ${request.id} is not found"))
    } yield Converters.fromModelVersion(version)
    f.value.rethrow.toIO.unsafeToFuture()
  }

  override def deployServable(request: DeployServableRequest, responseObserver: StreamObserver[Servable]): Unit = {
    val flow = for {
      res <- request.modelVersion match {
        case ModelVersion.Empty =>  F.raiseError[DeferredResult[F, GenericServable]](DomainError.invalidRequest("model version is not specified"))
        case ModelVersion.VersionId(value) => servableService.findAndDeploy(value, request.metadata)
        case ModelVersion.Fullname(value) => servableService.findAndDeploy(value.name, value.version, request.metadata) 
      }
      _ <- F.delay(responseObserver.onNext(Converters.fromServable(res.started)))
      completed <- res.completed.get
      _ <- F.delay(responseObserver.onNext(Converters.fromServable(completed)))
      _ <- F.delay(responseObserver.onCompleted())
    } yield ()

    flow
      .onError { case x => F.delay(responseObserver.onError(x)) }
      .toIO.unsafeRunAsyncAndForget()
  }

  override def removeServable(request: RemoveServableRequest): Future[Servable] = {
    val flow = for {
      res <- servableService.stop(request.servableName)
    } yield Converters.fromServable(res)
    flow.toIO.unsafeToFuture()
  }

  override def getServables(request: GetServablesRequest, responseObserver: StreamObserver[Servable]): Unit = {
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