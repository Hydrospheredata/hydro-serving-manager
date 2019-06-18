package io.hydrosphere.serving.manager.api.grpc

import cats.effect.ConcurrentEffect
import io.grpc.Server
import io.hydrosphere.serving.discovery.serving.ServingDiscoveryGrpc
import io.hydrosphere.serving.grpc.BuilderWrapper
import io.hydrosphere.serving.manager.api.ManagerServiceGrpc
import io.hydrosphere.serving.manager.{Repositories, Services}
import io.hydrosphere.serving.manager.config.ManagerConfiguration
import io.hydrosphere.serving.manager.discovery.{ApplicationSubscriber, ServableSubscriber}

import scala.concurrent.ExecutionContext

object GrpcApiServer {
  def apply[F[_] : ConcurrentEffect](
    managerRepositories: Repositories[F],
    managerServices: Services[F],
    managerConfiguration: ManagerConfiguration,
    appsHub: ApplicationSubscriber[F],
    servablesHub: ServableSubscriber[F]
  )(implicit ex: ExecutionContext): Server = {

    val managerGrpcService = new ManagerGrpcService(managerRepositories.modelVersionRepository, managerServices.servableService)
    val discoveryService = new GrpcServingDiscovery[F](appsHub, servablesHub)

    val builder = BuilderWrapper(io.grpc.ServerBuilder.forPort(managerConfiguration.application.grpcPort))
      .addService(ManagerServiceGrpc.bindService(managerGrpcService, ex))
      .addService(ServingDiscoveryGrpc.bindService(discoveryService, ex))

    builder.build
  }
}
