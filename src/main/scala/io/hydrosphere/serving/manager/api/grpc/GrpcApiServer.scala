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
    repos: Repositories[F],
    services: Services[F],
    config: ManagerConfiguration,
    appsHub: ApplicationSubscriber[F],
    servablesHub: ServableSubscriber[F]
  )(implicit ex: ExecutionContext): Server = {

    val managerGrpcService = new ManagerGrpcService(repos.modelVersionRepository, services.servableService)
    val discoveryService = new GrpcServingDiscovery[F](appsHub, servablesHub, repos.applicationRepository, repos.servableRepository)

    val builder = BuilderWrapper(io.grpc.ServerBuilder.forPort(config.application.grpcPort))
      .addService(ManagerServiceGrpc.bindService(managerGrpcService, ex))
      .addService(ServingDiscoveryGrpc.bindService(discoveryService, ex))

    builder.build
  }
}
