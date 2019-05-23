package io.hydrosphere.serving.manager.api.grpc

import cats.effect.Effect
import io.grpc.Server
import io.hydrosphere.serving.discovery.serving.ServingDiscoveryGrpc
import io.hydrosphere.serving.grpc.BuilderWrapper
import io.hydrosphere.serving.manager.api.ManagerServiceGrpc
import io.hydrosphere.serving.manager.{Repositories, Services}
import io.hydrosphere.serving.manager.config.ManagerConfiguration
import io.hydrosphere.serving.manager.discovery.application.DiscoveryGrpc.GrpcServingDiscovery
import io.hydrosphere.serving.manager.discovery.application.ObservedApplicationDiscoveryHub

import scala.concurrent.ExecutionContext

object GrpcApiServer {
  def apply[F[_]: Effect](
    managerRepositories: Repositories[F],
    managerServices: Services[F],
    managerConfiguration: ManagerConfiguration,
    discoveryHub: ObservedApplicationDiscoveryHub[F]
  )(implicit ex: ExecutionContext): Server = {

    val managerGrpcService = new ManagerGrpcService(managerRepositories.modelVersionRepository)
    val discoveryService = ServingDiscoveryGrpc.bindService(new GrpcServingDiscovery[F](discoveryHub), ExecutionContext.global)

    val builder = BuilderWrapper(io.grpc.ServerBuilder.forPort(managerConfiguration.application.grpcPort))
      .addService(ManagerServiceGrpc.bindService(managerGrpcService, ex))
      .addService(discoveryService)

    builder.build
  }
}
